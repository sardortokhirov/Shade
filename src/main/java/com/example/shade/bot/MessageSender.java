package com.example.shade.bot;

import com.example.shade.model.UserSession;
import com.example.shade.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private final UserSessionService sessionService;
    private AbsSender bot;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    public void sendMessage(SendMessage message, Long chatId) {
        try {
            message.setChatId(chatId);
            var sentMessage = bot.execute(message);
            UserSession session = sessionService.getUserSession(chatId).orElse(new UserSession());
            session.setChatId(chatId);
            List<Integer> messageIds = sessionService.getMessageIds(chatId);
            messageIds.add(sentMessage.getMessageId());
            session.setMessageIds(messageIds);
            sessionService.saveUserSession(session);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessage(Long chatId, String text) {
        int maxLength = 4096;

        // Split and send in parts
        for (int start = 0; start < text.length(); start += maxLength) {
            int end = Math.min(start + maxLength, text.length());
            String chunk = text.substring(start, end);

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(chunk);
            sendMessage(message, chatId); // existing method
        }
    }

    public void sendMessage(Long chatId, String text, ReplyKeyboard replyMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(replyMarkup);
        sendMessage(message, chatId);
    }

    // ========== MEDIA SENDING METHODS ==========

    public void sendPhoto(Long chatId, String fileId, String caption) {
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                sendPhoto.setCaption(caption);
            }
            bot.execute(sendPhoto);
            logger.info("Sent photo to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending photo to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendVideo(Long chatId, String fileId, String caption) {
        try {
            SendVideo sendVideo = new SendVideo();
            sendVideo.setChatId(chatId.toString());
            sendVideo.setVideo(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                sendVideo.setCaption(caption);
            }
            bot.execute(sendVideo);
            logger.info("Sent video to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending video to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendDocument(Long chatId, String fileId, String caption) {
        try {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId.toString());
            sendDocument.setDocument(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                sendDocument.setCaption(caption);
            }
            bot.execute(sendDocument);
            logger.info("Sent document to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending document to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendAudio(Long chatId, String fileId, String caption) {
        try {
            SendAudio sendAudio = new SendAudio();
            sendAudio.setChatId(chatId.toString());
            sendAudio.setAudio(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                sendAudio.setCaption(caption);
            }
            bot.execute(sendAudio);
            logger.info("Sent audio to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending audio to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendVoice(Long chatId, String fileId, String caption) {
        try {
            SendVoice sendVoice = new SendVoice();
            sendVoice.setChatId(chatId.toString());
            sendVoice.setVoice(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                sendVoice.setCaption(caption);
            }
            bot.execute(sendVoice);
            logger.info("Sent voice to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending voice to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendAnimation(Long chatId, String fileId, String caption) {
        try {
            SendAnimation sendAnimation = new SendAnimation();
            sendAnimation.setChatId(chatId.toString());
            sendAnimation.setAnimation(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                sendAnimation.setCaption(caption);
            }
            bot.execute(sendAnimation);
            logger.info("Sent animation to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending animation to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendSticker(Long chatId, String fileId) {
        try {
            SendSticker sendSticker = new SendSticker();
            sendSticker.setChatId(chatId.toString());
            sendSticker.setSticker(new InputFile(fileId));
            bot.execute(sendSticker);
            logger.info("Sent sticker to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending sticker to chatId {}: {}", chatId, e.getMessage());
        }
    }

    public void sendVideoNote(Long chatId, String fileId) {
        try {
            SendVideoNote sendVideoNote = new SendVideoNote();
            sendVideoNote.setChatId(chatId.toString());
            sendVideoNote.setVideoNote(new InputFile(fileId));
            bot.execute(sendVideoNote);
            logger.info("Sent video note to chatId {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending video note to chatId {}: {}", chatId, e.getMessage());
        }
    }

    // ========== FORWARDING METHODS ==========

    public boolean forwardMessage(Long toChatId, Long fromChatId, Integer messageId) {
        try {
            ForwardMessage forwardMessage = new ForwardMessage();
            forwardMessage.setChatId(toChatId.toString());
            forwardMessage.setFromChatId(fromChatId.toString());
            forwardMessage.setMessageId(messageId);
            bot.execute(forwardMessage);
            logger.info("Forwarded message {} from chat {} to chat {}", messageId, fromChatId, toChatId);
            return true;
        } catch (TelegramApiException e) {
            logger.error("Error forwarding message {} from chat {} to chat {}: {}", 
                messageId, fromChatId, toChatId, e.getMessage());
            return false;
        }
    }

    public boolean copyMessage(Long toChatId, Long fromChatId, Integer messageId) {
        try {
            CopyMessage copyMessage = new CopyMessage();
            copyMessage.setChatId(toChatId.toString());
            copyMessage.setFromChatId(fromChatId.toString());
            copyMessage.setMessageId(messageId);
            bot.execute(copyMessage);
            logger.info("Copied message {} from chat {} to chat {}", messageId, fromChatId, toChatId);
            return true;
        } catch (TelegramApiException e) {
            logger.error("Error copying message {} from chat {} to chat {}: {}", 
                messageId, fromChatId, toChatId, e.getMessage());
            return false;
        }
    }

    // ========== SMART MESSAGE SENDING (DETECTS TYPE) ==========

    public boolean sendMessageBasedOnType(Long chatId, Message originalMessage) {
        try {
            // For media messages, use copyMessage to avoid file ID issues between bots
            if (originalMessage.hasPhoto() || originalMessage.hasVideo() || 
                originalMessage.hasDocument() || originalMessage.hasAudio() || 
                originalMessage.hasVoice() || originalMessage.hasAnimation() || 
                originalMessage.hasSticker() || originalMessage.hasVideoNote()) {

                return copyMessage(chatId, originalMessage.getChatId(), originalMessage.getMessageId());
            } else if (originalMessage.hasText()) {
                sendMessage(chatId, originalMessage.getText());
                return true;
            } else {
                logger.warn("Unsupported message type for chatId {}", chatId);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error sending message based on type to chatId {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    // ========== EXISTING METHODS ==========

    public void editMessageToRemoveButtons(Long chatId, Integer messageId) {
        EditMessageReplyMarkup editMessage = new EditMessageReplyMarkup();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setReplyMarkup(null); // Remove keyboard
        try {
            bot.execute(editMessage);
            logger.info("Removed buttons from message {} in chat {}", messageId, chatId);
        } catch (TelegramApiException e) {
            logger.error("Failed to remove buttons from message {} in chat {}: {}", messageId, chatId, e.getMessage());
        }
    }

    public void animateAndDeleteMessages(Long chatId, List<Integer> messageIds, String animationType) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        for (Integer messageId : messageIds) {
            try {
                bot.execute(new DeleteMessage(String.valueOf(chatId), messageId));
            } catch (TelegramApiException e) {
                if (!e.getMessage().contains("message to delete not found")) {
                    logger.error("Error deleting message {} for chatId {}: {}", messageId, chatId, e.getMessage());
                }
            }
        }
    }
}