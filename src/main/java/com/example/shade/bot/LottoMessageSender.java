package com.example.shade.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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

    @Value("${telegram.lotto.log.button.bot.url:https://t.me/Baronpeybot?start=ref_5692494190}")
    private String lottoLogBotButtonUrl;

    @Value("${telegram.lotto.log.button.admin.url:https://t.me/BaronPey}")
    private String lottoLogAdminButtonUrl;

    @Value("${telegram.lotto.log.button.chat.url:https://t.me/BaronPey_Kassa}")
    private String lottoLogChatButtonUrl;

    @Value("${telegram.lotto.log.button.bot.label:Avtobot}")
    private String lottoLogBotButtonLabel;

    @Value("${telegram.lotto.log.button.admin.label:Admin}")
    private String lottoLogAdminButtonLabel;

    @Value("${telegram.lotto.log.button.chat.label:Chat}")
    private String lottoLogChatButtonLabel;

    public void setBot(AbsSender bot) {
        this.bot = bot;
    }

    private InlineKeyboardMarkup createLottoLogKeyboard() {
        InlineKeyboardMarkup inlineMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton botBtn = new InlineKeyboardButton();
        botBtn.setText(lottoLogBotButtonLabel);
        botBtn.setUrl(lottoLogBotButtonUrl);
        InlineKeyboardButton adminBtn = new InlineKeyboardButton();
        adminBtn.setText(lottoLogAdminButtonLabel);
        adminBtn.setUrl(lottoLogAdminButtonUrl);
        InlineKeyboardButton chatBtn = new InlineKeyboardButton();
        chatBtn.setText(lottoLogChatButtonLabel);
        chatBtn.setUrl(lottoLogChatButtonUrl);
        row.add(botBtn);
        row.add(adminBtn);
        row.add(chatBtn);
        rows.add(row);
        inlineMarkup.setKeyboard(rows);
        return inlineMarkup;
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(createLottoLogKeyboard());

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