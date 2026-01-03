package com.example.shade.service;

import com.example.shade.bot.AdminBotMessageSender;
import com.example.shade.bot.MessageSender;
import com.example.shade.model.BlockedUser;
import com.example.shade.repository.BlockedUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class BroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);
    private final BlockedUserRepository blockedUserRepository;
    private final TaskScheduler taskScheduler;
    private final MessageSender messageSender;
    @Lazy
    private final AdminBotMessageSender adminBotMessageSender;

    @Async("broadcastExecutor")
    public CompletableFuture<Void> sendBroadcast(String messageText, String parseMode, String buttonText, String buttonUrl, LocalDateTime scheduledTime, Long adminChatId) {
        try {
            // Validate parseMode
            String effectiveParseMode = parseMode != null && parseMode.equalsIgnoreCase("HTML") ? "HTML" : null;
            if (effectiveParseMode != null && !isValidHtml(messageText)) {
                logger.warn("Invalid HTML in messageText: {}", messageText);
                throw new IllegalArgumentException("Invalid HTML content in message text.");
            }

            // Prepare the message
            InlineKeyboardMarkup markup;
            if (buttonText != null && !buttonText.trim().isEmpty() && buttonUrl != null && !buttonUrl.trim().isEmpty()) {
                markup = createButtonMarkup(buttonText, buttonUrl);
            } else {
                markup = null;
            }

            // Get all non-blocked users
            List<BlockedUser> users = blockedUserRepository.findAllByPhoneNumberNot("BLOCKED");
            logger.info("Found {} non-blocked users for broadcast", users.size());

            // Track success and failure counts
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // Create broadcast task
            Runnable broadcastTask = () -> {
                try {
                    // Process in batches
                    int batchSize = 100;
                    int totalBatches = (int) Math.ceil((double) users.size() / batchSize);

                    for (int i = 0; i < users.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, users.size());
                        List<BlockedUser> batch = users.subList(i, end);

                        // Process batch
                        processBatch(batch, messageText, effectiveParseMode, markup, successCount, failureCount);

                        // Send progress update
                        int currentBatch = (i / batchSize) + 1;
                        int progress = (currentBatch * 100) / totalBatches;
                        if (adminChatId != null && adminBotMessageSender != null) {
                            sendProgressUpdate(adminChatId, progress, currentBatch, totalBatches,
                                    successCount.get(), failureCount.get());
                        }

                        // Delay between batches to respect Telegram rate limits
                        if (i + batchSize < users.size()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                logger.warn("Broadcast interrupted");
                                break;
                            }
                        }
                    }

                    logger.info("Broadcast completed: {} successful, {} failed", successCount.get(), failureCount.get());

                    // Send final completion message
                    if (adminChatId != null && adminBotMessageSender != null) {
                        String completionMessage = String.format(
                                "✅ Broadcast yakunlandi!\n\n" +
                                "✔️ Muvaffaqiyatli: %d\n" +
                                "❌ Xato: %d\n" +
                                "📊 Jami: %d",
                                successCount.get(), failureCount.get(), users.size());
                        adminBotMessageSender.sendTextMessage(adminChatId, completionMessage);
                    }
                } catch (Exception e) {
                    logger.error("Error in broadcast task", e);
                    if (adminChatId != null && adminBotMessageSender != null) {
                        adminBotMessageSender.sendTextMessage(adminChatId, "❌ Broadcast xatosi: " + e.getMessage());
                    }
                }
            };

            // Schedule or execute immediately
            if (scheduledTime != null && scheduledTime.isAfter(LocalDateTime.now(ZoneId.of("GMT+5")))) {
                taskScheduler.schedule(broadcastTask, scheduledTime.atZone(ZoneId.systemDefault()).toInstant());
                logger.info("Broadcast scheduled for {}", scheduledTime);
                return CompletableFuture.completedFuture(null);
            } else {
                // Execute immediately in background
                broadcastTask.run();
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception e) {
            logger.error("Error initiating broadcast", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private void processBatch(List<BlockedUser> batch, String messageText, String parseMode, InlineKeyboardMarkup markup,
                             AtomicInteger successCount, AtomicInteger failureCount) {
        for (BlockedUser user : batch) {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(user.getChatId());
                message.setText(messageText);
                if (parseMode != null) {
                    message.setParseMode(parseMode);
                }
                if (markup != null) {
                    message.setReplyMarkup(markup);
                }
                messageSender.sendMessage(message, user.getChatId());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                logger.error("Failed to send broadcast to user {}: {}", user.getChatId(), e.getMessage());
            }
        }
    }

    private void sendProgressUpdate(Long adminChatId, int progress, int currentBatch, int totalBatches,
                                   int successCount, int failureCount) {
        String progressMessage = String.format(
                "⏳ Broadcast jarayoni: %d%%\n" +
                "📦 Batch: %d / %d\n" +
                "✅ Muvaffaqiyatli: %d\n" +
                "❌ Xato: %d",
                progress, currentBatch, totalBatches, successCount, failureCount);

        try {
            adminBotMessageSender.sendTextMessage(adminChatId, progressMessage);
        } catch (Exception e) {
            logger.error("Failed to send progress update to admin {}", adminChatId, e);
        }
    }

    private InlineKeyboardMarkup createButtonMarkup(String buttonText, String buttonUrl) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setUrl(buttonUrl);
        rows.add(List.of(button));
        markup.setKeyboard(rows);
        return markup;
    }

    private boolean isValidHtml(String messageText) {
        // Basic validation for Telegram-supported HTML tags
        // Telegram supports: <b>, <i>, <a>, <code>, <pre>, <s>, <u>, <em>, <strong>, <tg-spoiler>, <tg-emoji>
        String[] allowedTags = {"b", "i", "a", "code", "pre", "s", "u", "em", "strong", "tg-spoiler", "tg-emoji"};
        for (String tag : allowedTags) {
            messageText = messageText.replaceAll("<" + tag + "[^>]*>", "").replaceAll("</" + tag + ">", "");
        }
        // Check if any HTML tags remain
        return !messageText.matches(".*<[a-zA-Z]+[^>]*>.*");
    }
}