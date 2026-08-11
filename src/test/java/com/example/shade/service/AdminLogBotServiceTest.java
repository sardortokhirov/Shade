package com.example.shade.service;

import com.example.shade.bot.AdminTelegramMessageSender;
import com.example.shade.model.AdminChat;
import com.example.shade.model.BlockedUser;
import com.example.shade.model.Currency;
import com.example.shade.model.ExchangeRate;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.repository.AdminCardRepository;
import com.example.shade.repository.AdminChatRepository;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.ExchangeRateRepository;
import com.example.shade.repository.HizmatRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminLogBotServiceTest {
    private AdminTelegramMessageSender sender;
    private AdminChatRepository adminChatRepository;
    private HizmatRequestRepository requestRepository;
    private ExchangeRateRepository exchangeRateRepository;
    private AdminCardRepository adminCardRepository;
    private BlockedUserRepository blockedUserRepository;
    private AdminLogBotService service;

    @BeforeEach
    void setUp() {
        sender = mock(AdminTelegramMessageSender.class);
        adminChatRepository = mock(AdminChatRepository.class);
        requestRepository = mock(HizmatRequestRepository.class);
        exchangeRateRepository = mock(ExchangeRateRepository.class);
        adminCardRepository = mock(AdminCardRepository.class);
        blockedUserRepository = mock(BlockedUserRepository.class);
        service = new AdminLogBotService(
                sender,
                adminChatRepository,
                requestRepository,
                exchangeRateRepository,
                adminCardRepository,
                blockedUserRepository);

        when(adminChatRepository.findByReceiveNotificationsTrue())
                .thenReturn(List.of(AdminChat.builder().chatId(999L).receiveNotifications(true).build()));
    }

    @Test
    void screenshotCaptionUsesExplicitRequestInsteadOfAnotherPendingAmount() {
        long userChatId = 8361495536L;
        HizmatRequest currentRequest = request(122219L, userChatId, 250070L, RequestStatus.PENDING_SCREENSHOT);
        when(requestRepository.findById(122219L)).thenReturn(Optional.of(currentRequest));
        when(exchangeRateRepository.findLatest()).thenReturn(Optional.of(ExchangeRate.builder()
                .uzsToRub(new BigDecimal("6.25"))
                .rubToUzs(new BigDecimal("160"))
                .createdAt(LocalDateTime.now())
                .build()));
        when(blockedUserRepository.findByChatId(userChatId)).thenReturn(Optional.of(
                BlockedUser.builder().chatId(userChatId).phoneNumber("+998770286604").build()));

        SendPhoto photo = mock(SendPhoto.class);
        when(photo.getPhoto()).thenReturn(mock(InputFile.class));

        service.sendScreenshotRequest(photo, userChatId, 122219L);

        ArgumentCaptor<String> caption = ArgumentCaptor.forClass(String.class);
        verify(photo).setCaption(caption.capture());
        assertTrue(caption.getValue().contains("250,070 UZS"));
        assertFalse(caption.getValue().contains("600,028 UZS"));
        verify(requestRepository, never())
                .findFirstByChatIdAndStatusOrderByCreatedAtDesc(any(), any());
        verify(sender).sendScreenshotRequest(photo, 999L);
    }

    @Test
    void screenshotIsRejectedWhenRequestBelongsToAnotherUser() {
        HizmatRequest anotherUsersRequest = request(122218L, 111L, 600028L, RequestStatus.PENDING_SCREENSHOT);
        when(requestRepository.findById(122218L)).thenReturn(Optional.of(anotherUsersRequest));

        SendPhoto photo = mock(SendPhoto.class);
        when(photo.getPhoto()).thenReturn(mock(InputFile.class));

        service.sendScreenshotRequest(photo, 222L, 122218L);

        verify(photo, never()).setCaption(any());
        verify(sender, never()).sendScreenshotRequest(eq(photo), any());
    }

    private HizmatRequest request(Long id, Long chatId, Long uniqueAmount, RequestStatus status) {
        return HizmatRequest.builder()
                .id(id)
                .chatId(chatId)
                .platform("1XBETUZS")
                .platformUserId("1488168147")
                .currency(Currency.UZS)
                .amount(uniqueAmount - 70)
                .uniqueAmount(uniqueAmount)
                .status(status)
                .type(RequestType.TOP_UP)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
