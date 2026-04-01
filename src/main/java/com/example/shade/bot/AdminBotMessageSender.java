package com.example.shade.bot;

import com.example.shade.model.AdminCard;
import com.example.shade.model.OsonConfig;
import com.example.shade.model.PaymentSystem;
import com.example.shade.model.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AdminBotMessageSender {

    private final ShadePaymentBot shadePaymentBot;

    @Autowired
    public AdminBotMessageSender(@Lazy ShadePaymentBot shadePaymentBot) {
        this.shadePaymentBot = shadePaymentBot;
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message", e);
        }
    }

    public void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🎛 Admin Panel\n\nKerakli bo'limni tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("⚙️ Funksiyalar", "features_menu"));
        rows.add(createRow("💳 Admin Kartalar", "cards_menu"));
        rows.add(createRow("🌐 Platformalar", "platforms_menu"));
        rows.add(createRow("🔧 Oson Config", "oson_menu"));
        rows.add(createRow("💱 Valyuta kursi", "exchange_menu"));
        rows.add(createRow("🎰 Lottery", "lottery_menu"));
        rows.add(createRow("📨 Xabar Yuborish", "forward_message"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending main menu", e);
        }
    }

    // ========== FEATURES ==========
    public void sendFeaturesMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⚙️ Funksiyalarni boshqarish\n\nKerakli funksiyani tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("💰 To'ldirish yoq/o'chir", "toggle_topup"));
        rows.add(createRow("💸 Yechib olish yoq/o'chir", "toggle_withdraw"));
        rows.add(createRow("🎁 Bonus yoq/o'chir", "toggle_bonus"));
        rows.add(createRow("💳 Hamyon yoq/o'chir", "toggle_wallet"));
        rows.add(createRow("📊 Bonus Limiti yoq/o'chir", "toggle_bonus_limit"));
        rows.add(createRow("🟩 HUMO yoq/o'chir", "toggle_humo"));
        rows.add(createRow("🔀 UZ tekshiruv (Oson/CardXabar)", "features_uz_rail"));
        rows.add(createRow("🔙 Ortga", "main_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending features menu", e);
        }
    }

    /** Global system UZ mode (not per-card). Callbacks: {@code config_set_uz_rail_OSON}, {@code CARDXABAR}, {@code OFF}. */
    public void sendUzRailConfigMenu(Long chatId, String currentMode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔀 UZ to'lovlari (global)\n\nHozirgi rejim: " + currentMode + "\n\nTanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createRow("Oson API", "config_set_uz_rail_OSON"));
        rows.add(createRow("CardXabar (2806)", "config_set_uz_rail_CARDXABAR"));
        rows.add(createRow("UZ o'chiq (Oson+CardXabar)", "config_set_uz_rail_OFF"));
        rows.add(createRow("🔙 Funksiyalar", "features_menu"));
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending uz rail config menu", e);
        }
    }

    // ========== CARDS ==========
    public void sendCardsMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💳 Admin Kartalar\n\nKerakli amalni tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("📋 Barcha kartalar", "cards_list_all"));
        rows.add(createRow("🔍 OsonConfig bo'yicha", "cards_by_oson"));
        rows.add(createRow("🔎 ID bo'yicha qidirish", "card_get"));
        rows.add(createRow("➕ Karta qo'shish", "card_add"));
        rows.add(createRow("✏️ Kartani yangilash", "card_update"));
        rows.add(createRow("🗑 Kartani o'chirish", "card_delete"));
        rows.add(createRow("🔙 Ortga", "main_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending cards menu", e);
        }
    }

    public void sendCardsList(Long chatId, List<AdminCard> cards) {
        StringBuilder text = new StringBuilder("💳 Admin Kartalar:\n\n");

        for (AdminCard card : cards) {
            text.append("🆔 ID: ").append(card.getId()).append("\n");
            text.append("💳 Karta: ").append(maskCardNumber(card.getCardNumber())).append("\n");
            text.append("👤 Egasi: ").append(card.getOwnerName()).append("\n");
            text.append("💰 Balans: ").append(formatBalance(card.getBalance())).append("\n");
            text.append("🏦 To'lov tizimi: ").append(card.getPaymentSystem()).append("\n");
            if (card.getPaymentSystem() == PaymentSystem.UZCARD && card.getUzcardRail() != null) {
                text.append("🔀 UZ tekshiruv: ").append(card.getUzcardRail()).append("\n");
            }
            text.append("⚙️ OsonConfig: ").append(card.getOsonConfig().getPhone()).append("\n");
            if (card.getLastUsed() != null) {
                text.append("🕐 Oxirgi foydalanish: ").append(card.getLastUsed().format(DATE_FORMATTER)).append("\n");
            }
            text.append("\n");
        }

        sendTextMessage(chatId, text.toString());
    }

    public void sendCardDetails(Long chatId, AdminCard card) {
        StringBuilder text = new StringBuilder("💳 Karta ma'lumotlari:\n\n");
        text.append("🆔 ID: ").append(card.getId()).append("\n");
        text.append("💳 Karta raqami: ").append(maskCardNumber(card.getCardNumber())).append("\n");
        text.append("👤 Egasi: ").append(card.getOwnerName()).append("\n");
        text.append("💰 Balans: ").append(formatBalance(card.getBalance())).append("\n");
        text.append("🏦 To'lov tizimi: ").append(card.getPaymentSystem()).append("\n");
        if (card.getPaymentSystem() == PaymentSystem.UZCARD && card.getUzcardRail() != null) {
            text.append("🔀 UZ tekshiruv: ").append(card.getUzcardRail()).append("\n");
        }
        text.append("⚙️ OsonConfig: ").append(card.getOsonConfig().getPhone()).append("\n");
        if (card.getLastUsed() != null) {
            text.append("🕐 Oxirgi foydalanish: ").append(card.getLastUsed().format(DATE_FORMATTER)).append("\n");
        }

        sendTextMessage(chatId, text.toString());
    }

    public void sendOsonConfigSelection(Long chatId, List<OsonConfig> configs) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔧 OsonConfig tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (OsonConfig config : configs) {
            String label = config.getPhone() + (config.isPrimaryConfig() ? " ⭐" : "");
            rows.add(createRow(label, "oson_cards_" + config.getId()));
        }
        rows.add(createRow("🔙 Ortga", "cards_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending oson config selection", e);
        }
    }

    public void sendPaymentSystemSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🏦 To'lov tizimini tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("🟦 UZCARD", "card_payment_UZCARD"));
        rows.add(createRow("🟩 HUMO", "card_payment_HUMO"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending payment system selection", e);
        }
    }

    /**
     * @param forUpdate if true, callback data uses prefix {@code card_update_uz_rail_} for edit flow
     */
    public void sendUzcardRailSelection(Long chatId, boolean forUpdate) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔀 UZCARD tekshiruv yo'lini tanlang (Oson yoki CardXabar):");

        String prefix = forUpdate ? "card_update_uz_rail_" : "card_uz_rail_";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createRow("Oson API", prefix + "OSON"));
        rows.add(createRow("CardXabar (2805)", prefix + "CARDXABAR"));
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending uzcard rail selection", e);
        }
    }

    public void sendCurrencySelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💱 Valyutani tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("🇷🇺 RUB", "platform_currency_RUB"));
        rows.add(createRow("🇺🇿 UZS", "platform_currency_UZS"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending currency selection", e);
        }
    }

    // ========== PLATFORMS ==========
    public void sendPlatformsMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🌐 Platformalar\n\nKerakli amalni tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("📋 Barcha platformalar", "platforms_list"));
        rows.add(createRow("🔎 ID bo'yicha qidirish", "platform_get"));
        rows.add(createRow("➕ Platforma qo'shish", "platform_create"));
        rows.add(createRow("✏️ Platformani yangilash", "platform_update"));
        rows.add(createRow("🗑 Platformani o'chirish", "platform_delete"));
        rows.add(createRow("🔙 Ortga", "main_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending platforms menu", e);
        }
    }

    // NEW METHOD FOR PLATFORM TYPE SELECTION
    public void sendPlatformTypeSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Platforma turini tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("⚙️ common", "platform_type_common"));
        rows.add(createRow("🎰 mostbet", "platform_type_mostbet"));

        rows.add(createRow("🔙 Ortga", "platforms_menu")); // Add back button

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending platform type selection", e);
        }
    }

    public void sendPlatformsList(Long chatId, List<Platform> platforms) {
        StringBuilder text = new StringBuilder("🌐 Platformalar:\n\n");

        for (Platform platform : platforms) {
            text.append("🆔 ID: ").append(platform.getId()).append("\n");
            text.append("📛 Nomi: ").append(platform.getName()).append("\n");
            text.append("📊 Turi: ").append(platform.getType()).append("\n");
            text.append("💱 Valyuta: ").append(platform.getCurrency()).append("\n");
            text.append("🔑 API Key: ").append(maskPassword(platform.getApiKey())).append("\n");

            // Display fields based on type
            if ("mostbet".equalsIgnoreCase(platform.getType())) {
                text.append("🔒 Secret: ").append(maskPassword(platform.getSecret())).append("\n");
            } else { // common
                text.append("👤 Login: ").append(platform.getLogin()).append("\n");
            }

            if (platform.getWorkplaceId() != null) {
                text.append("🏢 Workplace ID: ").append(platform.getWorkplaceId()).append("\n");
            }
            text.append("\n");
        }

        sendTextMessage(chatId, text.toString());
    }

    public void sendPlatformDetails(Long chatId, Platform platform) {
        StringBuilder text = new StringBuilder("🌐 Platforma ma'lumotlari:\n\n");
        text.append("🆔 ID: ").append(platform.getId()).append("\n");
        text.append("📛 Nomi: ").append(platform.getName()).append("\n");
        text.append("📊 Turi: ").append(platform.getType()).append("\n");
        text.append("💱 Valyuta: ").append(platform.getCurrency()).append("\n");
        text.append("🔑 API Key: ").append(maskPassword(platform.getApiKey())).append("\n");

        if ("mostbet".equalsIgnoreCase(platform.getType())) {
            text.append("🔒 Secret: ").append(maskPassword(platform.getSecret())).append("\n");
        } else { // common
            text.append("👤 Login: ").append(platform.getLogin()).append("\n");
            text.append("🔐 Password: ").append(maskPassword(platform.getPassword())).append("\n");
        }

        text.append("🏢 Workplace ID: ").append(platform.getWorkplaceId()).append("\n");

        sendTextMessage(chatId, text.toString());
    }

    // ========== OSON CONFIG ==========
    public void sendOsonConfigMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔧 Oson Config\n\nKerakli amalni tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("📋 Barcha configlar", "oson_list"));
        rows.add(createRow("🔎 ID bo'yicha qidirish", "oson_get"));
        rows.add(createRow("➕ Config qo'shish", "oson_save"));
        rows.add(createRow("✏️ Configni yangilash", "oson_update"));
        rows.add(createRow("⭐ Asosiy config qilish", "oson_set_primary"));
        rows.add(createRow("🗑 Configni o'chirish", "oson_delete"));
        rows.add(createRow("🔙 Ortga", "main_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending oson config menu", e);
        }
    }

    public void sendOsonConfigsList(Long chatId, List<OsonConfig> configs) {
        StringBuilder text = new StringBuilder("🔧 Oson Configs:\n\n");

        for (OsonConfig config : configs) {
            text.append("🆔 ID: ").append(config.getId()).append("\n");
            text.append("📱 Telefon: ").append(config.getPhone()).append("\n");
            text.append("🔑 Password: ").append(maskPassword(config.getPassword())).append("\n");
            text.append("🌐 API URL: ").append(config.getApiUrl()).append("\n");
            text.append("🔐 API Key: ").append(maskPassword(config.getApiKey())).append("\n");
            text.append("📱 Device ID: ").append(config.getDeviceId()).append("\n");
            text.append("📱 Device Name: ").append(config.getDeviceName()).append("\n");
            text.append("⭐ Asosiy: ").append(config.isPrimaryConfig() ? "Ha" : "Yo'q").append("\n\n");
        }

        sendTextMessage(chatId, text.toString());
    }

    public void sendOsonConfigDetails(Long chatId, OsonConfig config) {
        StringBuilder text = new StringBuilder("🔧 OsonConfig ma'lumotlari:\n\n");
        text.append("🆔 ID: ").append(config.getId()).append("\n");
        text.append("📱 Telefon: ").append(config.getPhone()).append("\n");
        text.append("🔑 Password: ").append(maskPassword(config.getPassword())).append("\n");
        text.append("🌐 API URL: ").append(config.getApiUrl()).append("\n");
        text.append("🔐 API Key: ").append(maskPassword(config.getApiKey())).append("\n");
        text.append("📱 Device ID: ").append(config.getDeviceId()).append("\n");
        text.append("📱 Device Name: ").append(config.getDeviceName()).append("\n");
        text.append("⭐ Asosiy: ").append(config.isPrimaryConfig() ? "Ha" : "Yo'q").append("\n");

        sendTextMessage(chatId, text.toString());
    }

    // ========== EXCHANGE RATE ==========
    public void sendExchangeRateMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💱 Valyuta kursi\n\nKerakli amalni tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("📊 Joriy kurs", "exchange_get"));
        rows.add(createRow("✏️ Kursni yangilash", "exchange_update"));
        rows.add(createRow("🔙 Ortga", "main_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending exchange rate menu", e);
        }
    }

    // ========== UTILITY METHODS ==========
    private List<InlineKeyboardButton> createRow(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        return row;
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return cardNumber;
        }
        return cardNumber.substring(0, 4) + " **** **** " + cardNumber.substring(12);
    }

    private String maskPassword(String password) {
        if (password == null || password.length() < 4) {
            return "****";
        }
        return password.substring(0, 2) + "****" + password.substring(password.length() - 2);
    }

    private String formatBalance(Long balance) {
        return String.format("%,d UZS", balance);
    }

    // ========== LOTTERY ==========
    public void sendLotteryMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🎰 Lottery boshqaruvi\n\nKerakli amalni tanlang:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(createRow("➕ Sovrin qo'shish", "lottery_add_prize"));
        rows.add(createRow("📋 Sovrinlar ro'yxati", "lottery_get_prizes"));
        rows.add(createRow("🗑 Sovrin o'chirish", "lottery_delete_prize"));
        rows.add(createRow("💰 Balans ko'rish", "lottery_get_balance"));
        rows.add(createRow("🎫 Biletlarni o'chirish", "lottery_delete_tickets"));
        rows.add(createRow("💸 Balansni o'chirish", "lottery_delete_balance"));
        rows.add(createRow("➕ Bilet qo'shish", "lottery_add_tickets"));
        rows.add(createRow("🎁 Random award", "lottery_award_random"));
        rows.add(createRow("🔙 Ortga", "main_menu"));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            shadePaymentBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending lottery menu", e);
        }
    }
}