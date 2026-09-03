package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.*;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.TicketListingRepository;
import com.example.shade.repository.UserBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketMarketplaceServiceTest {

    private MessageSender messageSender;
    private UserSessionService sessionService;
    private LanguageSessionService languageSessionService;
    private UserBalanceRepository userBalanceRepository;
    private TicketListingRepository ticketListingRepository;
    private HizmatRequestRepository requestRepository;
    private LotteryConfigService lotteryConfigService;
    private AdminLogBotService adminLogBotService;
    private BlockedUserRepository blockedUserRepository;
    private TicketMarketplaceService service;

    @BeforeEach
    void setUp() {
        messageSender = mock(MessageSender.class);
        sessionService = mock(UserSessionService.class);
        languageSessionService = mock(LanguageSessionService.class);
        userBalanceRepository = mock(UserBalanceRepository.class);
        ticketListingRepository = mock(TicketListingRepository.class);
        requestRepository = mock(HizmatRequestRepository.class);
        lotteryConfigService = mock(LotteryConfigService.class);
        adminLogBotService = mock(AdminLogBotService.class);
        blockedUserRepository = mock(BlockedUserRepository.class);

        service = new TicketMarketplaceService(
                messageSender,
                sessionService,
                languageSessionService,
                userBalanceRepository,
                ticketListingRepository,
                requestRepository,
                lotteryConfigService,
                adminLogBotService,
                blockedUserRepository);
        ReflectionTestUtils.setField(service, "self", service);

        when(languageSessionService.getTranslation(anyLong(), anyString())).thenReturn("ok");
        when(lotteryConfigService.getP2pMinPricePerTicket()).thenReturn(1000L);
        when(lotteryConfigService.getP2pFeePercentage()).thenReturn(new BigDecimal("0.05"));
        when(blockedUserRepository.existsByChatId(anyLong())).thenReturn(false);
        when(blockedUserRepository.findByChatId(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void createListingEscrowsTicketsFromSeller() {
        Long sellerId = 10L;
        UserBalance seller = UserBalance.builder()
                .chatId(sellerId)
                .tickets(20L)
                .balance(BigDecimal.ZERO)
                .walletBalance(0L)
                .build();
        when(userBalanceRepository.findByIdWithLock(sellerId)).thenReturn(Optional.of(seller));
        when(ticketListingRepository.save(any(TicketListing.class))).thenAnswer(inv -> {
            TicketListing listing = inv.getArgument(0);
            listing.setId(1L);
            return listing;
        });

        service.createListing(sellerId, 5L, 10_000L);

        assertEquals(15L, seller.getTickets());
        verify(userBalanceRepository).save(seller);
        verify(ticketListingRepository).save(argThat(l ->
                l.getTicketQuantity() == 5L
                        && l.getTotalPrice() == 10_000L
                        && l.getStatus() == TicketListingStatus.ACTIVE
                        && l.getSide() == TicketListingSide.SELL
                        && sellerId.equals(l.getSellerChatId())));
    }

    @Test
    void createListingRejectsBelowMinPricePerTicket() {
        Long sellerId = 10L;
        UserBalance seller = UserBalance.builder()
                .chatId(sellerId)
                .tickets(20L)
                .balance(BigDecimal.ZERO)
                .walletBalance(0L)
                .build();
        when(userBalanceRepository.findByIdWithLock(sellerId)).thenReturn(Optional.of(seller));

        service.createListing(sellerId, 5L, 1000L); // min would be 5000

        assertEquals(20L, seller.getTickets());
        verify(ticketListingRepository, never()).save(any());
    }

    @Test
    void buyListingTransfersWholeListingAndAppliesFee() {
        Long buyerId = 20L;
        Long sellerId = 10L;
        TicketListing listing = TicketListing.builder()
                .id(7L)
                .sellerChatId(sellerId)
                .side(TicketListingSide.SELL)
                .ticketQuantity(4L)
                .totalPrice(10_000L)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        UserBalance buyer = UserBalance.builder()
                .chatId(buyerId)
                .tickets(1L)
                .balance(BigDecimal.ZERO)
                .walletBalance(50_000L)
                .build();
        UserBalance seller = UserBalance.builder()
                .chatId(sellerId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .walletBalance(100L)
                .build();

        when(ticketListingRepository.findByIdWithLock(7L)).thenReturn(Optional.of(listing));
        when(userBalanceRepository.findByIdWithLock(sellerId)).thenReturn(Optional.of(seller));
        when(userBalanceRepository.findByIdWithLock(buyerId)).thenReturn(Optional.of(buyer));
        when(ticketListingRepository.findByStatusAndSideOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.buyListing(buyerId, 7L);

        assertEquals(40_000L, buyer.getWalletBalance());
        assertEquals(5L, buyer.getTickets());
        assertEquals(9600L, seller.getWalletBalance()); // 10000 - 5% fee
        assertEquals(TicketListingStatus.SOLD, listing.getStatus());
        assertEquals(buyerId, listing.getBuyerChatId());
        assertEquals(500L, listing.getFeeAmount());
        assertEquals(9500L, listing.getNetAmount());
        verify(requestRepository).save(argThat(r ->
                r.getType() == RequestType.TICKET_TRADE
                        && buyerId.equals(r.getChatId())
                        && sellerId.equals(r.getRecipientChatId())
                        && Long.valueOf(10_000L).equals(r.getAmount())
                        && Long.valueOf(500L).equals(r.getFeeAmount())
                        && Long.valueOf(9500L).equals(r.getNetAmount())));
    }

    @Test
    void cancelListingReturnsEscrowedTickets() {
        Long sellerId = 10L;
        TicketListing listing = TicketListing.builder()
                .id(3L)
                .sellerChatId(sellerId)
                .side(TicketListingSide.SELL)
                .ticketQuantity(8L)
                .totalPrice(20_000L)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        UserBalance seller = UserBalance.builder()
                .chatId(sellerId)
                .tickets(2L)
                .balance(BigDecimal.ZERO)
                .walletBalance(0L)
                .build();
        when(ticketListingRepository.findByIdWithLock(3L)).thenReturn(Optional.of(listing));
        when(userBalanceRepository.findByIdWithLock(sellerId)).thenReturn(Optional.of(seller));
        when(ticketListingRepository.findBySellerChatIdAndStatusOrderByCreatedAtDesc(eq(sellerId), any()))
                .thenReturn(java.util.List.of());

        service.cancelListing(sellerId, 3L);

        assertEquals(10L, seller.getTickets());
        assertEquals(TicketListingStatus.CANCELLED, listing.getStatus());
    }

    @Test
    void buyRejectsBuyingOwnListing() {
        Long sellerId = 10L;
        TicketListing listing = TicketListing.builder()
                .id(1L)
                .sellerChatId(sellerId)
                .side(TicketListingSide.SELL)
                .ticketQuantity(2L)
                .totalPrice(5000L)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        when(ticketListingRepository.findByIdWithLock(1L)).thenReturn(Optional.of(listing));

        service.buyListing(sellerId, 1L);

        verify(userBalanceRepository, never()).save(any());
        assertEquals(TicketListingStatus.ACTIVE, listing.getStatus());
    }

    @Test
    void createBuyOfferLocksWalletMoney() {
        Long buyerId = 20L;
        UserBalance buyer = UserBalance.builder()
                .chatId(buyerId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .walletBalance(50_000L)
                .build();
        when(userBalanceRepository.findByIdWithLock(buyerId)).thenReturn(Optional.of(buyer));
        when(ticketListingRepository.save(any(TicketListing.class))).thenAnswer(inv -> {
            TicketListing listing = inv.getArgument(0);
            listing.setId(11L);
            return listing;
        });

        service.createBuyOffer(buyerId, 3L, 12_000L);

        assertEquals(38_000L, buyer.getWalletBalance());
        verify(ticketListingRepository).save(argThat(l ->
                l.getSide() == TicketListingSide.BUY_OFFER
                        && l.getTicketQuantity() == 3L
                        && l.getTotalPrice() == 12_000L
                        && l.getStatus() == TicketListingStatus.ACTIVE
                        && buyerId.equals(l.getSellerChatId())));
    }

    @Test
    void createBuyOfferRejectsInsufficientWallet() {
        Long buyerId = 20L;
        UserBalance buyer = UserBalance.builder()
                .chatId(buyerId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .walletBalance(5_000L)
                .build();
        when(userBalanceRepository.findByIdWithLock(buyerId)).thenReturn(Optional.of(buyer));

        service.createBuyOffer(buyerId, 3L, 12_000L);

        assertEquals(5_000L, buyer.getWalletBalance());
        verify(ticketListingRepository, never()).save(any());
    }

    @Test
    void fulfillOfferMovesTicketsAndPaysSellerNet() {
        Long buyerId = 20L;
        Long sellerId = 10L;
        TicketListing listing = TicketListing.builder()
                .id(9L)
                .sellerChatId(buyerId)
                .side(TicketListingSide.BUY_OFFER)
                .ticketQuantity(4L)
                .totalPrice(10_000L)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        UserBalance buyer = UserBalance.builder()
                .chatId(buyerId)
                .tickets(1L)
                .balance(BigDecimal.ZERO)
                .walletBalance(0L)
                .build();
        UserBalance seller = UserBalance.builder()
                .chatId(sellerId)
                .tickets(10L)
                .balance(BigDecimal.ZERO)
                .walletBalance(100L)
                .build();

        when(ticketListingRepository.findByIdWithLock(9L)).thenReturn(Optional.of(listing));
        when(userBalanceRepository.findByIdWithLock(sellerId)).thenReturn(Optional.of(seller));
        when(userBalanceRepository.findByIdWithLock(buyerId)).thenReturn(Optional.of(buyer));
        when(ticketListingRepository.findByStatusAndSideOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.fulfillOffer(sellerId, 9L);

        assertEquals(5L, buyer.getTickets());
        assertEquals(6L, seller.getTickets());
        assertEquals(9600L, seller.getWalletBalance());
        assertEquals(TicketListingStatus.SOLD, listing.getStatus());
        assertEquals(sellerId, listing.getBuyerChatId());
        assertEquals(500L, listing.getFeeAmount());
        assertEquals(9500L, listing.getNetAmount());
        verify(requestRepository).save(argThat(r ->
                r.getType() == RequestType.TICKET_TRADE
                        && buyerId.equals(r.getChatId())
                        && sellerId.equals(r.getRecipientChatId())
                        && Long.valueOf(10_000L).equals(r.getAmount())
                        && Long.valueOf(500L).equals(r.getFeeAmount())
                        && Long.valueOf(9500L).equals(r.getNetAmount())));
    }

    @Test
    void fulfillOfferRejectsOwnOffer() {
        Long buyerId = 20L;
        TicketListing listing = TicketListing.builder()
                .id(9L)
                .sellerChatId(buyerId)
                .side(TicketListingSide.BUY_OFFER)
                .ticketQuantity(2L)
                .totalPrice(5000L)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        when(ticketListingRepository.findByIdWithLock(9L)).thenReturn(Optional.of(listing));

        service.fulfillOffer(buyerId, 9L);

        verify(userBalanceRepository, never()).save(any());
        assertEquals(TicketListingStatus.ACTIVE, listing.getStatus());
    }

    @Test
    void cancelBuyOfferRefundsWallet() {
        Long buyerId = 20L;
        TicketListing listing = TicketListing.builder()
                .id(12L)
                .sellerChatId(buyerId)
                .side(TicketListingSide.BUY_OFFER)
                .ticketQuantity(3L)
                .totalPrice(15_000L)
                .status(TicketListingStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        UserBalance buyer = UserBalance.builder()
                .chatId(buyerId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .walletBalance(1_000L)
                .build();
        when(ticketListingRepository.findByIdWithLock(12L)).thenReturn(Optional.of(listing));
        when(userBalanceRepository.findByIdWithLock(buyerId)).thenReturn(Optional.of(buyer));
        when(ticketListingRepository.findBySellerChatIdAndStatusOrderByCreatedAtDesc(eq(buyerId), any()))
                .thenReturn(java.util.List.of());

        service.cancelListing(buyerId, 12L);

        assertEquals(16_000L, buyer.getWalletBalance());
        assertEquals(TicketListingStatus.CANCELLED, listing.getStatus());
    }
}
