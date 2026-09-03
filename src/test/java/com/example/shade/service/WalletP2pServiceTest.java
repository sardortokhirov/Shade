package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.Currency;
import com.example.shade.model.RequestType;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletP2pServiceTest {

    private UserBalanceRepository userBalanceRepository;
    private HizmatRequestRepository requestRepository;
    private BlockedUserRepository blockedUserRepository;
    private SystemConfigurationService configurationService;
    private UserSessionService sessionService;
    private LanguageSessionService languageSessionService;
    private MessageSender messageSender;
    private AdminLogBotService adminLogBotService;
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        messageSender = mock(MessageSender.class);
        sessionService = mock(UserSessionService.class);
        userBalanceRepository = mock(UserBalanceRepository.class);
        requestRepository = mock(HizmatRequestRepository.class);
        languageSessionService = mock(LanguageSessionService.class);
        adminLogBotService = mock(AdminLogBotService.class);
        configurationService = mock(SystemConfigurationService.class);
        blockedUserRepository = mock(BlockedUserRepository.class);

        walletService = new WalletService(
                messageSender,
                sessionService,
                userBalanceRepository,
                requestRepository,
                mock(PlatformRepository.class),
                languageSessionService,
                adminLogBotService,
                configurationService,
                mock(BotTipConfigurationService.class),
                mock(LotteryService.class),
                mock(TopUpService.class),
                mock(MostbetService.class),
                mock(UserWalletQuotaRepository.class),
                mock(ExchangeRateRepository.class),
                mock(LottoBotService.class),
                mock(DailyStatsService.class),
                blockedUserRepository,
                mock(BonusService.class),
                mock(UserLimitIncreaseService.class));
        ReflectionTestUtils.setField(walletService, "self", walletService);

        when(languageSessionService.getTranslation(anyLong(), anyString())).thenReturn("ok");
        when(configurationService.getWalletToWalletFeePercentage()).thenReturn(new BigDecimal("0.05"));
        when(blockedUserRepository.existsByChatId(anyLong())).thenReturn(false);
        when(blockedUserRepository.findByChatId(anyLong())).thenReturn(Optional.empty());
        when(sessionService.beginOneShot(anyLong(), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount")))
                .thenAnswer(inv -> Optional.of("10000"));
        when(configurationService.getWalletTransferMinAmount()).thenReturn(1L);
        when(configurationService.getWalletTransferMaxAmount()).thenReturn(100_000_000L);
    }

    @Test
    void processWalletToWalletDebitsSenderCreditsNetToReceiver() {
        Long senderId = 1L;
        Long receiverId = 2L;
        when(sessionService.getUserData(senderId, "p2pRecipientId")).thenReturn(String.valueOf(receiverId));
        when(sessionService.beginOneShot(eq(senderId), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount")))
                .thenReturn(Optional.of("10000"));

        UserBalance sender = UserBalance.builder()
                .chatId(senderId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .walletBalance(20_000L)
                .build();
        UserBalance receiver = UserBalance.builder()
                .chatId(receiverId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .walletBalance(1_000L)
                .build();
        when(userBalanceRepository.findByIdWithLock(senderId)).thenReturn(Optional.of(sender));
        when(userBalanceRepository.findByIdWithLock(receiverId)).thenReturn(Optional.of(receiver));
        when(requestRepository.save(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, com.example.shade.model.HizmatRequest.class);
            req.setId(99L);
            return req;
        });

        walletService.processWalletToWallet(senderId);

        assertEquals(10_000L, sender.getWalletBalance());
        assertEquals(10_500L, receiver.getWalletBalance()); // 1000 + 9500
        verify(requestRepository).save(argThat(r ->
                r.getType() == RequestType.WALLET_TO_WALLET
                        && senderId.equals(r.getChatId())
                        && receiverId.equals(r.getRecipientChatId())
                        && Long.valueOf(10_000L).equals(r.getAmount())
                        && Long.valueOf(500L).equals(r.getFeeAmount())
                        && Long.valueOf(9500L).equals(r.getNetAmount())
                        && r.getCurrency() == Currency.UZS));
    }

    @Test
    void processWalletToWalletRejectsSelfTransfer() {
        when(sessionService.getUserData(1L, "p2pRecipientId")).thenReturn("1");
        when(sessionService.beginOneShot(eq(1L), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount")))
                .thenReturn(Optional.of("5000"));

        walletService.processWalletToWallet(1L);

        verify(userBalanceRepository, never()).findByIdWithLock(anyLong());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void processWalletToWalletRejectsBlockedRecipient() {
        when(sessionService.getUserData(1L, "p2pRecipientId")).thenReturn("2");
        when(sessionService.beginOneShot(eq(1L), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount")))
                .thenReturn(Optional.of("5000"));
        when(blockedUserRepository.findByChatId(2L)).thenReturn(Optional.of(
                com.example.shade.model.BlockedUser.builder()
                        .chatId(2L)
                        .phoneNumber("BLOCKED")
                        .build()));

        walletService.processWalletToWallet(1L);

        verify(userBalanceRepository, never()).findByIdWithLock(anyLong());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void processWalletToWalletRejectsInsufficientBalance() {
        when(sessionService.getUserData(1L, "p2pRecipientId")).thenReturn("2");
        when(sessionService.beginOneShot(eq(1L), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount")))
                .thenReturn(Optional.of("5000"));
        UserBalance sender = UserBalance.builder()
                .chatId(1L).tickets(0L).balance(BigDecimal.ZERO).walletBalance(100L).build();
        UserBalance receiver = UserBalance.builder()
                .chatId(2L).tickets(0L).balance(BigDecimal.ZERO).walletBalance(0L).build();
        when(userBalanceRepository.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(userBalanceRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));

        walletService.processWalletToWallet(1L);

        assertEquals(100L, sender.getWalletBalance());
        assertEquals(0L, receiver.getWalletBalance());
        verify(requestRepository, never()).save(any());
        verify(sessionService).setUserData(1L, "p2pAmount", "5000");
        verify(sessionService).setUserState(1L, "WALLET_P2P_CONFIRM");
    }

    @Test
    void processWalletToWalletIgnoresDuplicateConfirm() {
        when(sessionService.beginOneShot(eq(1L), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount")))
                .thenReturn(Optional.empty());

        walletService.processWalletToWallet(1L);

        verify(sessionService, never()).getUserData(anyLong(), eq("p2pRecipientId"));
        verify(userBalanceRepository, never()).findByIdWithLock(anyLong());
        verify(requestRepository, never()).save(any());
    }
}