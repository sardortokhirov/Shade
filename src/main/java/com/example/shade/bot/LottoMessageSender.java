package com.example.shade.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramBot;

import java.util.ArrayList;
import java.util.List;

/**
 * Date-7/23/2025
 * By Sardor Tokhirov
 * Time-3:23 AM (GMT+5)
 */

@Component
public class LottoMessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
    private AbsSender bot;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        InlineKeyboardMarkup inlineMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("Link to Bot");
        button.setUrl("https://t.me/xonpeybot");

        row.add(button);
        rows.add(row);
        inlineMarkup.setKeyboard(rows);

        message.setReplyMarkup(inlineMarkup);

        try {
            bot.execute(message);
            logger.info("Sent message to chatId {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chatId {}: {}", chatId, e.getMessage());
        }
    }


    public void sendMessage(String  chatId, String text, ReplyKeyboardMarkup replyMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        if (replyMarkup != null) {
            message.setReplyMarkup(replyMarkup);
        }
        try {
            bot.execute(message);
            logger.info("Sent message to chatId {}: {}", chatId, text);
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chatId {}: {}", chatId, e.getMessage());
        }
    }
}