package com.example.shade.bot;

import com.example.shade.model.Platform;
import com.example.shade.service.AdminBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShadeAdminUpdateHandler {

    private final AdminBotService adminBotService;
    private final AdminBotMessageSender adminBotMessageSender;
    private final Map<Long, BotState> userStates = new HashMap<>();
    private final Map<Long, Map<String, Object>> userContext = new HashMap<>();

    public boolean isUserInAdminState(Long chatId) {
        return userStates.containsKey(chatId);
    }

    public void setUserInAdminState(Long chatId) {
        userStates.put(chatId, null);
    }

    public void clearAdminSession(Long chatId) {
        userStates.remove(chatId);
        userContext.remove(chatId);
    }

    public boolean handleUpdate(Update update) {
        try {
            if (update.hasMessage()) {
                return handleMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
                return true;
            }
        } catch (Exception e) {
            log.error("Error processing admin update", e);
        }
        return false;
    }

    private boolean handleMessage(Update update) {
        Long chatId = update.getMessage().getChatId();

        if (update.getMessage().hasText()
                && ("/admin".equals(update.getMessage().getText()) || "/kassa".equals(update.getMessage().getText()))) {
            return false;
        }

        BotState state = userStates.get(chatId);

        // FIX: If state is null, and it's a text message (but not /admin or /kassa),
        // we assume the user typed something unexpected. Send them back to the main
        // menu.
        if (state == null) {
            if (update.getMessage().hasText()) {
                // Clear any leftover state just in case
                clearAdminSession(chatId);
                // Send back to main menu
                adminBotService.sendMainMenu(chatId);
                return true; // We handled it by navigating home
            }
            return false;
        }

        if (state == BotState.WAITING_FORWARD_MESSAGE) {
            Map<String, Object> context = userContext.getOrDefault(chatId, new HashMap<>());
            context.put("message", update.getMessage());
            userContext.put(chatId, context);
            userStates.put(chatId, BotState.WAITING_FORWARD_CONFIRMATION);
            adminBotService.requestForwardConfirmation(chatId, update.getMessage());
            return true;
        }

        if (update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            handleStateInput(chatId, text, state);
        } else {
            // If in a state (not WAITING_FORWARD_MESSAGE) and received non-text, prompt for
            // text
            adminBotService.requestInput(chatId, "❌ Iltimos, matn kiriting.");
        }
        return true;
    }

    private void handleCallbackQuery(Update update) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        switch (data) {
            // Main menu
            case "main_menu" -> adminBotService.sendMainMenu(chatId);

            // Feature toggles
            case "features_menu" -> adminBotService.sendFeaturesMenu(chatId);
            case "toggle_topup" -> adminBotService.toggleTopUp(chatId);
            case "toggle_withdraw" -> adminBotService.toggleWithdraw(chatId);
            case "toggle_bonus" -> adminBotService.toggleBonus(chatId);
            case "toggle_bonus_limit" -> adminBotService.toggleBonusLimit(chatId);

            // Admin Cards
            case "cards_menu" -> adminBotService.sendCardsMenu(chatId);
            case "cards_list_all" -> adminBotService.getAllCards(chatId);
            case "cards_by_oson" -> adminBotService.sendOsonConfigsForCards(chatId);
            case "card_get" -> {
                userStates.put(chatId, BotState.WAITING_CARD_ID);
                adminBotService.requestInput(chatId, "Karta ID sini kiriting:");
            }
            case "card_add" -> {
                userStates.put(chatId, BotState.WAITING_CARD_NUMBER);
                userContext.put(chatId, new HashMap<>());
                adminBotService.requestInput(chatId, "Karta raqamini kiriting (16 raqam):");
            }
            case "card_update" -> {
                userStates.put(chatId, BotState.WAITING_CARD_UPDATE_ID);
                adminBotService.requestInput(chatId, "Yangilanadigan karta ID sini kiriting:");
            }
            case "card_delete" -> {
                userStates.put(chatId, BotState.WAITING_CARD_DELETE_ID);
                adminBotService.requestInput(chatId, "O'chiriladigan karta ID sini kiriting:");
            }

            // Platforms
            case "platforms_menu" -> adminBotService.sendPlatformsMenu(chatId);
            case "platforms_list" -> adminBotService.getAllPlatforms(chatId);
            case "platform_get" -> {
                userStates.put(chatId, BotState.WAITING_PLATFORM_ID);
                adminBotService.requestInput(chatId, "Platforma ID sini kiriting:");
            }
            case "platform_create" -> {
                userStates.put(chatId, BotState.WAITING_PLATFORM_TYPE);
                userContext.put(chatId, new HashMap<>());
                adminBotMessageSender.sendPlatformTypeSelection(chatId);
            }
            case "platform_type_common" -> {
                Map<String, Object> context = userContext.getOrDefault(chatId, new HashMap<>());
                context.put("type", "common");
                userContext.put(chatId, context);
                userStates.put(chatId, BotState.WAITING_PLATFORM_NAME);
                adminBotService.requestInput(chatId, "Platforma nomini kiriting (common):");
            }
            case "platform_type_mostbet" -> {
                Map<String, Object> context = userContext.getOrDefault(chatId, new HashMap<>());
                context.put("type", "mostbet");
                userContext.put(chatId, context);
                userStates.put(chatId, BotState.WAITING_PLATFORM_NAME);
                adminBotService.requestInput(chatId, "Platforma nomini kiriting (mostbet):");
            }
            case "platform_currency_RUB" -> {
                Map<String, Object> context = userContext.get(chatId);
                context.put("currency", "RUB");
                String type = (String) context.get("type");

                if ("common".equalsIgnoreCase(type)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_API_KEY);
                    adminBotService.requestInput(chatId, "API Key kiriting:");
                } else if ("mostbet".equalsIgnoreCase(type)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_API_KEY);
                    adminBotService.requestInput(chatId, "API Key kiriting:");
                } else {
                    log.error("Platform type not set in context: {}", chatId);
                    adminBotService.sendPlatformsMenu(chatId);
                }
            }
            case "platform_currency_UZS" -> {
                Map<String, Object> context = userContext.get(chatId);
                context.put("currency", "UZS");
                String type = (String) context.get("type");

                if ("common".equalsIgnoreCase(type)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_API_KEY);
                    adminBotService.requestInput(chatId, "API Key kiriting:");
                } else if ("mostbet".equalsIgnoreCase(type)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_API_KEY);
                    adminBotService.requestInput(chatId, "API Key kiriting:");
                } else {
                    log.error("Platform type not set in context: {}", chatId);
                    adminBotService.sendPlatformsMenu(chatId);
                }
            }
            case "platform_update" -> {
                userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_ID);
                adminBotService.requestInput(chatId, "Yangilanadigan platforma ID sini kiriting:");
            }
            case "platform_delete" -> {
                userStates.put(chatId, BotState.WAITING_PLATFORM_DELETE_ID);
                adminBotService.requestInput(chatId, "O'chiriladigan platforma ID sini kiriting:");
            }

            // OsonConfig
            case "oson_menu" -> adminBotService.sendOsonConfigMenu(chatId);
            case "oson_list" -> adminBotService.getAllOsonConfigs(chatId);
            case "oson_get" -> {
                userStates.put(chatId, BotState.WAITING_OSON_ID);
                adminBotService.requestInput(chatId, "OsonConfig ID sini kiriting:");
            }
            case "oson_save" -> {
                userStates.put(chatId, BotState.WAITING_OSON_PHONE);
                userContext.put(chatId, new HashMap<>());
                adminBotService.requestInput(chatId, "Telefon raqamini kiriting:");
            }
            case "oson_update" -> {
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_ID);
                adminBotService.requestInput(chatId, "Yangilanadigan OsonConfig ID sini kiriting:");
            }
            case "oson_set_primary" -> {
                userStates.put(chatId, BotState.WAITING_OSON_PRIMARY_ID);
                adminBotService.requestInput(chatId, "Asosiy qilinadigan OsonConfig ID sini kiriting:");
            }
            case "oson_delete" -> {
                userStates.put(chatId, BotState.WAITING_OSON_DELETE_ID);
                adminBotService.requestInput(chatId, "O'chiriladigan OsonConfig ID sini kiriting:");
            }

            // Exchange Rate
            case "exchange_menu" -> adminBotService.sendExchangeRateMenu(chatId);
            case "exchange_get" -> adminBotService.getLatestExchangeRate(chatId);
            case "exchange_update" -> {
                userStates.put(chatId, BotState.WAITING_EXCHANGE_RATE);
                adminBotService.requestInput(chatId, "Yangi kurs qiymatini kiriting (masalan: 12750.50):");
            }

            // Lottery
            case "lottery_menu" -> adminBotService.sendLotteryMenu(chatId);
            case "lottery_add_prize" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_PRIZE_NAME);
                userContext.put(chatId, new HashMap<>());
                adminBotService.requestInput(chatId, "Sovrin nomini kiriting:");
            }
            case "lottery_get_prizes" -> adminBotService.getAllPrizes(chatId);
            case "lottery_delete_prize" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_DELETE_ID);
                adminBotService.requestInput(chatId, "O'chiriladigan sovrin ID sini kiriting:");
            }
            case "lottery_get_balance" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_CHATID);
                adminBotService.requestInput(chatId, "Foydalanuvchi chat ID sini kiriting:");
            }
            case "lottery_delete_tickets" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_DELETE_TICKETS_CHATID);
                adminBotService.requestInput(chatId, "Foydalanuvchi chat ID sini kiriting:");
            }
            case "lottery_delete_balance" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_DELETE_BALANCE_CHATID);
                adminBotService.requestInput(chatId, "Foydalanuvchi chat ID sini kiriting:");
            }
            case "lottery_add_tickets" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_ADD_TICKETS_CHATID);
                userContext.put(chatId, new HashMap<>());
                adminBotService.requestInput(chatId, "Foydalanuvchi chat ID sini kiriting:");
            }
            case "lottery_award_random" -> {
                userStates.put(chatId, BotState.WAITING_LOTTERY_AWARD_TOTAL_USERS);
                userContext.put(chatId, new HashMap<>());
                adminBotService.requestInput(chatId, "Jami foydalanuvchilar sonini kiriting:");
            }

            // Message Forwarding
            case "forward_message" -> {
                userStates.put(chatId, BotState.WAITING_FORWARD_MESSAGE);
                userContext.put(chatId, new HashMap<>());
                adminBotService.requestInput(chatId, "Yuborish uchun xabarni yuboring (matn, rasm, video va h.k.):");
            }

            default -> {
                if (data.startsWith("oson_cards_")) {
                    Long osonId = Long.parseLong(data.replace("oson_cards_", ""));
                    adminBotService.getCardsByOsonConfig(chatId, osonId);
                } else if (data.startsWith("card_payment_")) {
                    String paymentSystem = data.replace("card_payment_", "");
                    Map<String, Object> context = userContext.get(chatId);
                    context.put("paymentSystem", paymentSystem);
                    userStates.put(chatId, BotState.WAITING_CARD_OSON_ID);
                    adminBotService.requestInput(chatId, "OsonConfig ID sini kiriting:");
                }
            }
        }
    }

    private void handleStateInput(Long chatId, String text, BotState state) {
        Map<String, Object> context = userContext.getOrDefault(chatId, new HashMap<>());
        String type = (String) context.get("type");

        switch (state) {
            // Card States
            case WAITING_CARD_ID -> {
                adminBotService.getCardById(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_CARD_NUMBER -> {
                context.put("cardNumber", text);
                userStates.put(chatId, BotState.WAITING_CARD_OWNER);
                adminBotService.requestInput(chatId, "Karta egasining nomini kiriting:");
            }
            case WAITING_CARD_OWNER -> {
                context.put("ownerName", text);
                userStates.put(chatId, BotState.WAITING_CARD_BALANCE);
                adminBotService.requestInput(chatId, "Karta balansini kiriting:");
            }
            case WAITING_CARD_BALANCE -> {
                context.put("balance", text);
                adminBotService.sendPaymentSystemSelection(chatId);
            }
            case WAITING_CARD_OSON_ID -> {
                context.put("osonConfigId", text);
                userContext.put(chatId, context);
                adminBotService.createCard(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_CARD_UPDATE_ID -> {
                context.put("updateCardId", text);
                userStates.put(chatId, BotState.WAITING_CARD_UPDATE_NUMBER);
                adminBotService.requestInput(chatId, "Yangi karta raqamini kiriting:");
            }
            case WAITING_CARD_UPDATE_NUMBER -> {
                context.put("cardNumber", text);
                userStates.put(chatId, BotState.WAITING_CARD_UPDATE_OWNER);
                adminBotService.requestInput(chatId, "Yangi egasi nomini kiriting:");
            }
            case WAITING_CARD_UPDATE_OWNER -> {
                context.put("ownerName", text);
                userStates.put(chatId, BotState.WAITING_CARD_UPDATE_BALANCE);
                adminBotService.requestInput(chatId, "Yangi balansni kiriting:");
            }
            case WAITING_CARD_UPDATE_BALANCE -> {
                context.put("balance", text);
                userContext.put(chatId, context);
                adminBotService.updateCard(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_CARD_DELETE_ID -> {
                adminBotService.deleteCard(chatId, text);
                userStates.remove(chatId);
            }

            // Platform States
            case WAITING_PLATFORM_ID -> {
                adminBotService.getPlatformById(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_PLATFORM_TYPE -> {
                adminBotMessageSender.sendPlatformTypeSelection(chatId);
            }
            case WAITING_PLATFORM_NAME -> {
                context.put("name", text);
                adminBotService.sendCurrencySelection(chatId);
            }
            case WAITING_PLATFORM_API_KEY -> {
                context.put("apiKey", text);

                if ("common".equalsIgnoreCase(type)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_LOGIN);
                    adminBotService.requestInput(chatId, "Login kiriting:");
                } else if ("mostbet".equalsIgnoreCase(type)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_SECRET);
                    adminBotService.requestInput(chatId, "Secret kiriting:");
                }
            }
            case WAITING_PLATFORM_SECRET -> {
                context.put("secret", text);
                userStates.put(chatId, BotState.WAITING_PLATFORM_WORKPLACE_ID_mostbet);
                adminBotService.requestInput(chatId, "Workplace ID kiriting:");
            }
            case WAITING_PLATFORM_LOGIN -> {
                context.put("login", text);
                userStates.put(chatId, BotState.WAITING_PLATFORM_PASSWORD);
                adminBotService.requestInput(chatId, "Password kiriting:");
            }
            case WAITING_PLATFORM_PASSWORD -> {
                context.put("password", text);
                userStates.put(chatId, BotState.WAITING_PLATFORM_WORKPLACE_ID);
                adminBotService.requestInput(chatId, "Workplace ID kiriting:");
            }
            case WAITING_PLATFORM_WORKPLACE_ID -> {
                context.put("workplaceId", text);
                userContext.put(chatId, context);
                adminBotService.createPlatform(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_PLATFORM_WORKPLACE_ID_mostbet -> {
                context.put("workplaceId", text);
                userContext.put(chatId, context);
                adminBotService.createPlatform(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }

            // Platform Update States
            case WAITING_PLATFORM_UPDATE_ID -> {
                try {
                    Long platformId = Long.parseLong(text);
                    Platform platform = adminBotService.getPlatformById(platformId);
                    context.put("updatePlatformId", text);
                    context.put("type", platform.getType());
                    userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_NAME);
                    adminBotService.requestInput(chatId, "Yangi platforma nomini kiriting:");
                } catch (Exception e) {
                    adminBotService.requestInput(chatId, "❌ Noto'g'ri ID. Yangilanadigan platforma ID sini kiriting:");
                }
            }
            case WAITING_PLATFORM_UPDATE_NAME -> {
                context.put("name", text);
                userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_API_KEY);
                adminBotService.requestInput(chatId, "Yangi API Key kiriting:");
            }
            case WAITING_PLATFORM_UPDATE_API_KEY -> {
                context.put("apiKey", text);
                userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_WORKPLACE_ID);
                adminBotService.requestInput(chatId, "Yangi Workplace ID kiriting:");
            }
            case WAITING_PLATFORM_UPDATE_WORKPLACE_ID -> {
                context.put("workplaceId", text);
                String updateType = (String) context.get("type");

                if ("common".equalsIgnoreCase(updateType)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_LOGIN);
                    adminBotService.requestInput(chatId, "Yangi Login kiriting:");
                } else if ("mostbet".equalsIgnoreCase(updateType)) {
                    userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_SECRET);
                    adminBotService.requestInput(chatId, "Yangi Secret kiriting (o'zgartirmasangiz - 'SKIP'):");
                } else {
                    adminBotService.updatePlatform(chatId, context);
                    userStates.remove(chatId);
                    userContext.remove(chatId);
                }
            }
            case WAITING_PLATFORM_UPDATE_LOGIN -> {
                context.put("login", text);
                userStates.put(chatId, BotState.WAITING_PLATFORM_UPDATE_PASSWORD);
                adminBotService.requestInput(chatId, "Yangi Password kiriting (o'zgartirmasangiz - 'SKIP'):");
            }
            case WAITING_PLATFORM_UPDATE_PASSWORD -> {
                context.put("password", "SKIP".equalsIgnoreCase(text) ? null : text);
                userContext.put(chatId, context);
                adminBotService.updatePlatform(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_PLATFORM_UPDATE_SECRET -> {
                context.put("secret", "SKIP".equalsIgnoreCase(text) ? null : text);
                userContext.put(chatId, context);
                adminBotService.updatePlatform(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_PLATFORM_DELETE_ID -> {
                adminBotService.deletePlatform(chatId, text);
                userStates.remove(chatId);
            }

            // OsonConfig States
            case WAITING_OSON_ID -> {
                adminBotService.getOsonConfigById(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_OSON_PHONE -> {
                context.put("phone", text);
                userStates.put(chatId, BotState.WAITING_OSON_PASSWORD);
                adminBotService.requestInput(chatId, "Password kiriting:");
            }
            case WAITING_OSON_PASSWORD -> {
                context.put("password", text);
                userStates.put(chatId, BotState.WAITING_OSON_API_URL);
                adminBotService.requestInput(chatId, "API URL kiriting:");
            }
            case WAITING_OSON_API_URL -> {
                context.put("apiUrl", text);
                userStates.put(chatId, BotState.WAITING_OSON_API_KEY);
                adminBotService.requestInput(chatId, "API Key kiriting:");
            }
            case WAITING_OSON_API_KEY -> {
                context.put("apiKey", text);
                userStates.put(chatId, BotState.WAITING_OSON_DEVICE_ID);
                adminBotService.requestInput(chatId, "Device ID kiriting:");
            }
            case WAITING_OSON_DEVICE_ID -> {
                context.put("deviceId", text);
                userStates.put(chatId, BotState.WAITING_OSON_DEVICE_NAME);
                adminBotService.requestInput(chatId, "Device Name kiriting:");
            }
            case WAITING_OSON_DEVICE_NAME -> {
                context.put("deviceName", text);
                userContext.put(chatId, context);
                adminBotService.saveOsonConfig(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_OSON_UPDATE_ID -> {
                context.put("updateOsonId", text);
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_PHONE);
                adminBotService.requestInput(chatId, "Yangi telefon raqamini kiriting:");
            }
            case WAITING_OSON_UPDATE_PHONE -> {
                context.put("phone", text);
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_PASSWORD);
                adminBotService.requestInput(chatId, "Yangi password kiriting:");
            }
            case WAITING_OSON_UPDATE_PASSWORD -> {
                context.put("password", text);
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_API_URL);
                adminBotService.requestInput(chatId, "Yangi API URL kiriting:");
            }
            case WAITING_OSON_UPDATE_API_URL -> {
                context.put("apiUrl", text);
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_API_KEY);
                adminBotService.requestInput(chatId, "Yangi API Key kiriting:");
            }
            case WAITING_OSON_UPDATE_API_KEY -> {
                context.put("apiKey", text);
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_DEVICE_ID);
                adminBotService.requestInput(chatId, "Yangi Device ID kiriting:");
            }
            case WAITING_OSON_UPDATE_DEVICE_ID -> {
                context.put("deviceId", text);
                userStates.put(chatId, BotState.WAITING_OSON_UPDATE_DEVICE_NAME);
                adminBotService.requestInput(chatId, "Yangi Device Name kiriting:");
            }
            case WAITING_OSON_UPDATE_DEVICE_NAME -> {
                context.put("deviceName", text);
                userContext.put(chatId, context);
                adminBotService.updateOsonConfig(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_OSON_PRIMARY_ID -> {
                adminBotService.setPrimaryConfig(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_OSON_DELETE_ID -> {
                adminBotService.deleteOsonConfig(chatId, text);
                userStates.remove(chatId);
            }

            // Exchange rate States
            case WAITING_EXCHANGE_RATE -> {
                adminBotService.updateExchangeRate(chatId, text);
                userStates.remove(chatId);
            }

            // Lottery States
            case WAITING_LOTTERY_PRIZE_NAME -> {
                context.put("prizeName", text);
                userStates.put(chatId, BotState.WAITING_LOTTERY_PRIZE_AMOUNT);
                adminBotService.requestInput(chatId, "Sovrin miqdorini kiriting (UZS):");
            }
            case WAITING_LOTTERY_PRIZE_AMOUNT -> {
                context.put("prizeAmount", text);
                userStates.put(chatId, BotState.WAITING_LOTTERY_PRIZE_COUNT);
                adminBotService.requestInput(chatId, "Sovrinlar sonini kiriting:");
            }
            case WAITING_LOTTERY_PRIZE_COUNT -> {
                context.put("prizeCount", text);
                userContext.put(chatId, context);
                adminBotService.addPrize(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_LOTTERY_DELETE_ID -> {
                adminBotService.deletePrize(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_LOTTERY_CHATID -> {
                adminBotService.getBalance(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_LOTTERY_DELETE_TICKETS_CHATID -> {
                adminBotService.deleteTickets(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_LOTTERY_DELETE_BALANCE_CHATID -> {
                adminBotService.deleteBalance(chatId, text);
                userStates.remove(chatId);
            }
            case WAITING_LOTTERY_ADD_TICKETS_CHATID -> {
                context.put("ticketChatId", text);
                userStates.put(chatId, BotState.WAITING_LOTTERY_ADD_TICKETS_AMOUNT);
                adminBotService.requestInput(chatId, "Qo'shiladigan biletlar sonini kiriting:");
            }
            case WAITING_LOTTERY_ADD_TICKETS_AMOUNT -> {
                context.put("ticketAmount", text);
                userContext.put(chatId, context);
                adminBotService.addTickets(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
            case WAITING_LOTTERY_AWARD_TOTAL_USERS -> {
                context.put("totalUsers", text);
                userStates.put(chatId, BotState.WAITING_LOTTERY_AWARD_RANDOM_USERS);
                adminBotService.requestInput(chatId, "Random tanlash uchun foydalanuvchilar sonini kiriting:");
            }
            case WAITING_LOTTERY_AWARD_RANDOM_USERS -> {
                context.put("randomUsers", text);
                userStates.put(chatId, BotState.WAITING_LOTTERY_AWARD_AMOUNT);
                adminBotService.requestInput(chatId, "Har bir foydalanuvchiga beriladigan miqdorni kiriting (UZS):");
            }
            case WAITING_LOTTERY_AWARD_AMOUNT -> {
                context.put("awardAmount", text);
                userContext.put(chatId, context);
                adminBotService.awardRandomUsers(chatId, context);
                userStates.remove(chatId);
                userContext.remove(chatId);
            }

            // Message Forwarding States
            case WAITING_FORWARD_CONFIRMATION -> {
                if ("ha".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
                    adminBotService.confirmAndForwardMessage(chatId, context);
                } else {
                    adminBotService.requestInput(chatId, "❌ Xabar yuborish bekor qilindi.");
                }
                userStates.remove(chatId);
                userContext.remove(chatId);
            }
        }
    }

    public enum BotState {
        // Card states
        WAITING_CARD_ID,
        WAITING_CARD_NUMBER,
        WAITING_CARD_OWNER,
        WAITING_CARD_BALANCE,
        WAITING_CARD_PAYMENT_SYSTEM,
        WAITING_CARD_OSON_ID,
        WAITING_CARD_UPDATE_ID,
        WAITING_CARD_UPDATE_NUMBER,
        WAITING_CARD_UPDATE_OWNER,
        WAITING_CARD_UPDATE_BALANCE,
        WAITING_CARD_DELETE_ID,

        // Platform states
        WAITING_PLATFORM_ID,
        WAITING_PLATFORM_TYPE,
        WAITING_PLATFORM_NAME,
        WAITING_PLATFORM_API_KEY,
        WAITING_PLATFORM_SECRET,
        WAITING_PLATFORM_LOGIN,
        WAITING_PLATFORM_PASSWORD,
        WAITING_PLATFORM_WORKPLACE_ID,
        WAITING_PLATFORM_WORKPLACE_ID_mostbet,
        WAITING_PLATFORM_UPDATE_ID,
        WAITING_PLATFORM_UPDATE_NAME,
        WAITING_PLATFORM_UPDATE_API_KEY,
        WAITING_PLATFORM_UPDATE_WORKPLACE_ID,
        WAITING_PLATFORM_UPDATE_LOGIN,
        WAITING_PLATFORM_UPDATE_PASSWORD,
        WAITING_PLATFORM_UPDATE_SECRET,
        WAITING_PLATFORM_DELETE_ID,

        // OsonConfig states
        WAITING_OSON_ID,
        WAITING_OSON_PHONE,
        WAITING_OSON_PASSWORD,
        WAITING_OSON_API_URL,
        WAITING_OSON_API_KEY,
        WAITING_OSON_DEVICE_ID,
        WAITING_OSON_DEVICE_NAME,
        WAITING_OSON_UPDATE_ID,
        WAITING_OSON_UPDATE_PHONE,
        WAITING_OSON_UPDATE_PASSWORD,
        WAITING_OSON_UPDATE_API_URL,
        WAITING_OSON_UPDATE_API_KEY,
        WAITING_OSON_UPDATE_DEVICE_ID,
        WAITING_OSON_UPDATE_DEVICE_NAME,
        WAITING_OSON_PRIMARY_ID,
        WAITING_OSON_DELETE_ID,

        // Exchange rate states
        WAITING_EXCHANGE_RATE,

        // Lottery states
        WAITING_LOTTERY_PRIZE_NAME,
        WAITING_LOTTERY_PRIZE_AMOUNT,
        WAITING_LOTTERY_PRIZE_COUNT,
        WAITING_LOTTERY_DELETE_ID,
        WAITING_LOTTERY_CHATID,
        WAITING_LOTTERY_DELETE_TICKETS_CHATID,
        WAITING_LOTTERY_DELETE_BALANCE_CHATID,
        WAITING_LOTTERY_ADD_TICKETS_CHATID,
        WAITING_LOTTERY_ADD_TICKETS_AMOUNT,
        WAITING_LOTTERY_AWARD_TOTAL_USERS,
        WAITING_LOTTERY_AWARD_RANDOM_USERS,
        WAITING_LOTTERY_AWARD_AMOUNT,

        // Message forwarding states
        WAITING_FORWARD_MESSAGE,
        WAITING_FORWARD_CONFIRMATION
    }
}