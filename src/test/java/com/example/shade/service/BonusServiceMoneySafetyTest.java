package com.example.shade.service;

import com.example.shade.model.Currency;
import com.example.shade.model.ExchangeRate;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.Platform;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BonusServiceMoneySafetyTest {

    @Mock private UserSessionService sessionService;
    @Mock private ReferralRepository referralRepository;
    @Mock private UserBalanceRepository userBalanceRepository;
    @Mock private PlatformRepository platformRepository;
    @Mock private HizmatRequestRepository requestRepository;
    @Mock private BlockedUserRepository blockedUserRepository;
    @Mock private AdminChatRepository adminChatRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private LotteryService lotteryService;
    @Mock private com.example.shade.bot.MessageSender messageSender;
    @Mock private AdminLogBotService adminLogBotService;
    @Mock private MostbetService mostbetService;
    @Mock private LanguageSessionService languageSessionService;
    @Mock private SystemConfigurationService systemConfigurationService;
    @Mock private FeatureService featureService;
    @Mock private DailyStatsService dailyStatsService;
    @Mock private PromoWhitelistService promoWhitelistService;
    @Mock private UserPlatformPermissionRepository permissionRepository;

    @InjectMocks
    private BonusService bonusService;

    @Test
    void declineWithRefundReturnsDeductedBonusExactlyOnce() {
        HizmatRequest request = request(10L, 100L, 50_000L);
        UserBalance balance = UserBalance.builder()
                .chatId(100L)
                .tickets(0L)
                .balance(BigDecimal.valueOf(25_000L))
                .build();

        when(requestRepository.findByIdWithLock(10L)).thenReturn(Optional.of(request));
        when(userBalanceRepository.findByIdWithLock(100L)).thenReturn(Optional.of(balance));
        when(blockedUserRepository.findByChatId(100L)).thenReturn(Optional.empty());
        when(languageSessionService.getTranslation(anyLong(), anyString())).thenReturn("ok");

        bonusService.handleAdminDeclineTransferWithRefund(999L, 10L);

        assertEquals(RequestStatus.CANCELED, request.getStatus());
        assertEquals(BigDecimal.valueOf(75_000L), balance.getBalance());
        verify(userBalanceRepository).save(balance);
        verify(requestRepository).save(request);
    }

    @Test
    void failedMostbetTransferIsNotMarkedApproved() throws Exception {
        HizmatRequest request = request(11L, 101L, 60_000L);
        Platform platform = new Platform();
        platform.setName("Mostbet");
        platform.setType("mostbet");
        platform.setCurrency(Currency.UZS);
        ExchangeRate rate = new ExchangeRate();
        rate.setUzsToRub(BigDecimal.ONE);

        when(requestRepository.findByIdWithLock(11L)).thenReturn(Optional.of(request));
        when(platformRepository.findByName("Mostbet")).thenReturn(Optional.of(platform));
        when(mostbetService.transferToPlatform(request)).thenReturn(null);
        when(exchangeRateRepository.findLatest()).thenReturn(Optional.of(rate));
        when(blockedUserRepository.findByChatId(101L)).thenReturn(Optional.empty());
        when(languageSessionService.getTranslation(anyLong(), anyString())).thenReturn("ok");

        bonusService.handleAdminApproveTransfer(999L, 11L);

        assertEquals(RequestStatus.PENDING_ADMIN, request.getStatus());
        verify(requestRepository, never()).save(request);
    }

    @Test
    void repeatedApproveDoesNotCallPlatformAgain() throws Exception {
        HizmatRequest request = request(12L, 102L, 70_000L);
        request.setStatus(RequestStatus.BONUS_APPROVED);

        when(requestRepository.findByIdWithLock(12L)).thenReturn(Optional.of(request));

        bonusService.handleAdminApproveTransfer(999L, 12L);

        verifyNoInteractions(mostbetService);
        verify(requestRepository, never()).save(request);
    }

    private HizmatRequest request(Long id, Long chatId, Long amount) {
        HizmatRequest request = new HizmatRequest();
        request.setId(id);
        request.setChatId(chatId);
        request.setPlatform("Mostbet");
        request.setPlatformUserId("12345");
        request.setAmount(amount);
        request.setUniqueAmount(amount);
        request.setCurrency(Currency.UZS);
        request.setStatus(RequestStatus.PENDING_ADMIN);
        return request;
    }
}
