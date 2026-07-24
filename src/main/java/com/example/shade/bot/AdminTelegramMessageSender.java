package com.example.shade.bot;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminTelegramMessageSender {
    private static final Logger logger = LoggerFactory.getLogger(AdminTelegramMessageSender.class);
    private AbsSender bot;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    public void sendMessage(Long chatId, String text) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.enableMarkdown(true);
        try {
            bot.execute(message);
            logger.info("Sent message to admin chatId {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            // Fallback without markdown if Telegram rejects the parse mode
            // (e.g. unescaped underscores in phone/card numbers).
            logger.warn("Markdown send failed for admin chatId {}, retrying plain: {}", chatId, e.getMessage());
            try {
                SendMessage plain = new SendMessage();
                plain.setChatId(chatId.toString());
                plain.setText(text);
                bot.execute(plain);
            } catch (TelegramApiException e2) {
                logger.error("Failed to send message to admin chatId {}: {}", chatId, e2.getMessage());
            }
        }
    }

    public void sendMessage(SendMessage sendMessage, Long chatId) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        try {
            bot.execute(sendMessage);
            logger.info("Sent message with keyboard to admin chatId {}: {}", chatId, sendMessage.getText());
        } catch (TelegramApiException e) {
            logger.warn("Send with keyboard failed for admin chatId {}, retrying plain text: {}", chatId, e.getMessage());
            try {
                // If markdown parse failed, retry without parse mode but keep keyboard.
                sendMessage.setParseMode(null);
                bot.execute(sendMessage);
            } catch (TelegramApiException e2) {
                logger.error("Failed to send message with keyboard to admin chatId {}: {}", chatId, e2.getMessage());
            }
        }
    }

    public void sendScreenshotRequest(SendPhoto sendPhoto, Long chatId) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        try {
            bot.execute(sendPhoto);
            logger.info("Sent screenshot request to admin chatId {}: {}", chatId, sendPhoto.getCaption());
        } catch (TelegramApiException e) {
            logger.error("Failed to send screenshot request to admin chatId {}: {}", chatId, e.getMessage());
        }
    }
    public void clearBotData(Long chatId, Integer messageId) {
        if (bot == null) {
            logger.error("Bot not set for AdminTelegramMessageSender for chatId: {}", chatId);
            return;
        }
        // Delete the specific message
        if (messageId != null) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(chatId.toString());
                deleteMessage.setMessageId(messageId);
                bot.execute(deleteMessage);
                logger.info("Deleted messageId {} for chatId {}", messageId, chatId);
            } catch (TelegramApiException e) {
                logger.error("Failed to delete messageId {} for chatId {}: {}", messageId, chatId, e.getMessage());
            }
        }
    }

    /** Remove inline buttons from an admin message without deleting the text. */
    public void removeInlineButtons(Long chatId, Integer messageId) {
        if (bot == null || chatId == null || messageId == null) {
            return;
        }
        try {
            EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setReplyMarkup(null);
            bot.execute(edit);
        } catch (TelegramApiException e) {
            logger.warn("Failed to remove buttons from admin message {}/{}: {}", chatId, messageId, e.getMessage());
        }
    }

    /**
     * Replace admin request message text and clear buttons.
     * Used so wallet withdraw requests keep a visible ✅/❌ status instead of "disappearing".
     */
    public void editMessageText(Long chatId, Integer messageId, String text) {
        if (bot == null || chatId == null || messageId == null || text == null) {
            return;
        }
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(text);
            edit.enableMarkdown(true);
            edit.setReplyMarkup(null);
            bot.execute(edit);
        } catch (TelegramApiException e) {
            logger.warn("Failed to edit admin message {}/{}: {}", chatId, messageId, e.getMessage());
            // Fallback: at least strip buttons so the spinner/actions do not hang.
            removeInlineButtons(chatId, messageId);
        }
    }
}