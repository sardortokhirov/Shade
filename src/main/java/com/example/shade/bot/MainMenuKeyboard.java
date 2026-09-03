package com.example.shade.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Shared payment-bot home keyboard with Telegram button styles
 * ({@code success}=green, {@code danger}=red, {@code primary}=blue).
 */
public final class MainMenuKeyboard {
    private static final String INSTRUCTION_URL = "https://t.me/BaronPeyInfo";

    private MainMenuKeyboard() {
    }

    public static InlineKeyboardMarkup build(BiFunction<Long, String, String> translate, Long chatId) {
        return build(
                translate.apply(chatId, "button.topup"),
                translate.apply(chatId, "button.withdraw"),
                translate.apply(chatId, "button.wallet"),
                translate.apply(chatId, "button.bonus"),
                translate.apply(chatId, "button.bozor"),
                translate.apply(chatId, "button.contact"),
                translate.apply(chatId, "button.instruction"));
    }

    public static InlineKeyboardMarkup build(
            String topup,
            String withdraw,
            String wallet,
            String bonus,
            String bozor,
            String contact,
            String instruction) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(StyledInlineKeyboardButton.callback(
                topup, "TOPUP", StyledInlineKeyboardButton.STYLE_SUCCESS)));
        rows.add(List.of(StyledInlineKeyboardButton.callback(
                withdraw, "WITHDRAW", StyledInlineKeyboardButton.STYLE_DANGER)));
        rows.add(List.of(StyledInlineKeyboardButton.callback(
                wallet, "WALLET", StyledInlineKeyboardButton.STYLE_PRIMARY)));
        rows.add(List.of(StyledInlineKeyboardButton.callback(
                bonus, "BONUS", StyledInlineKeyboardButton.STYLE_SUCCESS)));
        rows.add(List.of(StyledInlineKeyboardButton.callback(
                bozor, "BOZOR", StyledInlineKeyboardButton.STYLE_DANGER)));
        rows.add(List.of(StyledInlineKeyboardButton.callback(
                contact, "CONTACT", StyledInlineKeyboardButton.STYLE_PRIMARY)));
        rows.add(List.of(StyledInlineKeyboardButton.url(
                instruction, INSTRUCTION_URL, StyledInlineKeyboardButton.STYLE_PRIMARY)));
        markup.setKeyboard(rows);
        return markup;
    }
}