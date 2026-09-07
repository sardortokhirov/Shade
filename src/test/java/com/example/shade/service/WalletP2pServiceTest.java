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
                mock(LotteryService.class),
                mock(TopUpService.class),
                mock(MostbetService.class),
                mock(UserWalletQuotaRepository.class),
                mock(ExchangeRateRepository.class),
                blockedUserRepository,
                mock(BonusService.class),
                mock(BotTipConfigurationService.class),
                mock(UserLimitIncreaseService.class),
                mock(DailyStatsService.class));
        ReflectionTestUtils.setField(walletService, "self", walletService);

        when(languageSessionService.getTranslation(anyLong(), anyString())).thenReturn("ok");
        when(configurationService.getWalletToWalletFeePercentage()).thenReturn(new BigDecimal("0.05"));
        when(blockedUserRepository.existsByChatId(anyLong())).thenReturn(false);
        when(blockedUserRepository.findByChatId(anyLong())).thenReturn(Optional.empty());
        when(sessionService.beginOneShotKeys(eq(1L), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount"), eq("p2pRecipientId")))
                .thenReturn(Optional.of(java.util.Map.of("p2pAmount", "10000", "p2pRecipientId", "2")));
        when(configurationService.getWalletTransferMinAmount()).thenReturn(1L);
        when(configurationService.getWalletTransferMaxAmount()).thenReturn(100_000_000L);
    }

    private void stubOneShot(long senderId, String amount, String recipient) {
        when(sessionService.beginOneShotKeys(eq(senderId), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount"), eq("p2pRecipientId")))
                .thenReturn(Optional.of(java.util.Map.of("p2pAmount", amount, "p2pRecipientId", recipient)));
    }

    @Test
    void processWalletToWalletDebitsSenderCreditsNetToReceiver() {
        Long senderId = 1L;
        Long receiverId = 2L;
        stubOneShot(senderId, "10000", String.valueOf(receiverId));

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
        stubOneShot(1L, "5000", "1");

        walletService.processWalletToWallet(1L);

        verify(userBalanceRepository, never()).findByIdWithLock(anyLong());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void processWalletToWalletRejectsBlockedRecipient() {
        stubOneShot(1L, "5000", "2");
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
        stubOneShot(1L, "5000", "2");
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
        verify(sessionService).setUserData(1L, "p2pRecipientId", "2");
        verify(sessionService).setUserState(1L, "WALLET_P2P_CONFIRM");
    }

    @Test
    void processWalletToWalletIgnoresDuplicateConfirm() {
        when(sessionService.beginOneShotKeys(eq(1L), eq("WALLET_P2P_CONFIRM"), eq("WALLET_P2P_PROCESSING"), eq("p2pAmount"), eq("p2pRecipientId")))
                .thenReturn(Optional.empty());

        walletService.processWalletToWallet(1L);

        verify(userBalanceRepository, never()).findByIdWithLock(anyLong());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void processWalletToWalletAllowsWhenMaxIsZeroUnlimited() {
        when(configurationService.getWalletTransferMaxAmount()).thenReturn(0L);
        stubOneShot(1L, "10000", "2");
        UserBalance sender = UserBalance.builder()
                .chatId(1L).tickets(0L).balance(BigDecimal.ZERO).walletBalance(20_000L).build();
        UserBalance receiver = UserBalance.builder()
                .chatId(2L).tickets(0L).balance(BigDecimal.ZERO).walletBalance(0L).build();
        when(userBalanceRepository.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(userBalanceRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        when(requestRepository.save(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, com.example.shade.model.HizmatRequest.class);
            req.setId(7L);
            return req;
        });

        walletService.processWalletToWallet(1L);

        assertEquals(10_000L, sender.getWalletBalance());
        assertEquals(9_500L, receiver.getWalletBalance());
        verify(requestRepository).save(any());
    }

    @Test
    void notificationFailureDoesNotFailCommittedTransferOrLeaveProcessingSession() {
        stubOneShot(1L, "10000", "2");
        UserBalance sender = UserBalance.builder()
                .chatId(1L).tickets(0L).balance(BigDecimal.ZERO).walletBalance(20_000L).build();
        UserBalance receiver = UserBalance.builder()
                .chatId(2L).tickets(0L).balance(BigDecimal.ZERO).walletBalance(0L).build();
        when(userBalanceRepository.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(userBalanceRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        when(requestRepository.save(any())).thenAnswer(inv -> {
            var req = inv.getArgument(0, com.example.shade.model.HizmatRequest.class);
            req.setId(8L);
            return req;
        });
        doThrow(new RuntimeException("Telegram unavailable"))
                .when(messageSender).sendMessage(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class), eq(1L));

        walletService.processWalletToWallet(1L);

        assertEquals(10_000L, sender.getWalletBalance());
        assertEquals(9_500L, receiver.getWalletBalance());
        verify(requestRepository).save(any());
        verify(sessionService).clearSession(1L);
        verify(sessionService, atLeastOnce()).setUserState(1L, "MAIN_MENU");
    }
}
