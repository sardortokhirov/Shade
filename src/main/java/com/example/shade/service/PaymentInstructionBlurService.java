package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.PendingPaymentMessage;
import com.example.shade.repository.PendingPaymentMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentInstructionBlurService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentInstructionBlurService.class);
    private static final long BLUR_DELAY_MINUTES = 8L;

    private final PendingPaymentMessageRepository pendingPaymentMessageRepository;
    private final MessageSender messageSender;

    private static final Pattern BACKTICK_CARD_PATTERN = Pattern.compile("`([^`]+)`");

    @Scheduled(fixedRate = 60_000)
    public void blurOldPaymentInstructions() {
        LocalDateTime threshold = LocalDateTime.now(ZoneId.of("GMT+5")).minusMinutes(BLUR_DELAY_MINUTES);
        List<PendingPaymentMessage> pendingList =
                pendingPaymentMessageRepository.findByBlurredFalseAndCreatedAtBefore(threshold);
        if (pendingList.isEmpty()) {
            return;
        }

        for (PendingPaymentMessage pending : pendingList) {
            try {
                String blurredText = maskCardNumberInText(pending.getOriginalText());
                messageSender.editMessageText(pending.getChatId(), pending.getMessageId(), blurredText);
            } catch (Exception e) {
                logger.error("Failed to blur payment instruction for chatId {}, messageId {}: {}",
                        pending.getChatId(), pending.getMessageId(), e.getMessage());
            } finally {
                pending.setBlurred(true);
                pendingPaymentMessageRepository.save(pending);
            }
        }
    }

    private String maskCardNumberInText(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = BACKTICK_CARD_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        String inside = matcher.group(1);
        String digitsOnly = inside.replaceAll("\\D", "");
        if (digitsOnly.length() < 4) {
            return text;
        }
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        String maskedPrefix = "*".repeat(digitsOnly.length() - 4);

        StringBuilder grouped = new StringBuilder();
        String combined = maskedPrefix + last4;
        for (int i = 0; i < combined.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                grouped.append(' ');
            }
            grouped.append(combined.charAt(i));
        }

        String maskedInside = grouped.toString();
        String replacement = "`" + maskedInside + "`";
        int start = matcher.start(1) - 1;
        int end = matcher.end(1) + 1;
        return text.substring(0, start) + replacement + text.substring(end);
    }
}

