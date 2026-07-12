package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.dto.BalanceLimit;
import com.example.shade.dto.WalletUserIdValidationResult;
import com.example.shade.model.*;
import com.example.shade.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {
    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    private final MessageSender messageSender;
    private final UserSessionService sessionService;
    private final UserBalanceRepository userBalanceRepository;
    private final HizmatRequestRepository requestRepository;
    private final PlatformRepository platformRepository;
    private final LanguageSessionService languageSessionService;
    private final AdminLogBotService adminLogBotService;
    private final SystemConfigurationService configurationService;
    private final LotteryService lotteryService;
    private final TopUpService topUpService;
    private final MostbetService mostbetService;
    private final UserWalletQuotaRepository walletQuotaRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final BonusService bonusService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private WalletService self;

    // ----- ENTRY POINT -----

    public void startWallet(Long chatId, String source) {
        logger.info("Starting wallet for chatId: {}, source: {}", chatId, source);
        sessionService.setUserData(chatId, "walletSource", source);
        sessionService.setUserState(chatId, "WALLET_MENU");
        sessionService.addNavigationState(chatId, "MAIN_MENU");
        sendWalletMenu(chatId);
    }

    // ----- UI RENDERING -----

    private void sendWalletMenu(Long chatId) {
        UserBalance balance = getOrCreateUserBalance(chatId);
        Long walletAmount = balance.getWalletBalance();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.menu"),
                walletAmount));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Show all buttons regardless of source
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "wallet.button.topup"),
                        "WALLET_TOPUP")));
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "wallet.button.deposit"),
                        "WALLET_DEPOSIT"),
                createButton(languageSessionService.getTranslation(chatId, "wallet.button.withdraw"),
                        "WALLET_WITHDRAW")));

        // History button
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "wallet.button.history"),
                        "WALLET_HISTORY:0")));

        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendDepositPlatformSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(languageSessionService.getTranslation(chatId, "wallet.message.select_platform"));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<Platform> uzsPlatforms = platformRepository.findByCurrency(Currency.UZS);
        List<Platform> rubPlatforms = platformRepository.findByCurrency(Currency.RUB);

        int maxRows = Math.max(uzsPlatforms.size(), rubPlatforms.size());
        for (int i = 0; i < maxRows; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            if (i < uzsPlatforms.size()) {
                Platform p = uzsPlatforms.get(i);
                row.add(createButton("🇺🇿 " + p.getName(), "WALLET_PLATFORM:" + p.getName()));
            }
            if (i < rubPlatforms.size()) {
                Platform p = rubPlatforms.get(i);
                row.add(createButton("🇷🇺 " + p.getName(), "WALLET_PLATFORM:" + p.getName()));
            } else if (i < uzsPlatforms.size()) {
                i++;
                if (i < maxRows) {
                    Platform p = uzsPlatforms.get(i);
                    row.add(createButton("🇺🇿 " + p.getName(), "WALLET_PLATFORM:" + p.getName()));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendDepositIdInput(Long chatId, String platformName) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.enter_id"),
                escapeMarkdown(platformName != null ? platformName : "")));

        // Adding recent requests keyboard
        List<HizmatRequest> recentRequests = requestRepository.findTop3ByChatIdAndPlatformOrderByCreatedAtDesc(chatId,
                platformName);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!recentRequests.isEmpty()) {
            List<InlineKeyboardButton> pastIdButtons = recentRequests.stream()
                    .map(HizmatRequest::getPlatformUserId)
                    .distinct()
                    .limit(2)
                    .map(id -> createButton("🆔 " + id, "WALLET_PAST_ID:" + id))
                    .collect(Collectors.toList());
            if (!pastIdButtons.isEmpty()) {
                rows.add(pastIdButtons);
            }
        }
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendDepositAmountInput(Long chatId) {
        String platformUserId = sessionService.getUserData(chatId, "walletPlatformUserId");
        String fullName = sessionService.getUserData(chatId, "walletFullName");
        String fullNameDisplay = fullName != null && !fullName.isEmpty() ? fullName : "—";
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.enter_amount"),
                escapeMarkdown(fullNameDisplay),
                escapeMarkdown(platformUserId != null ? platformUserId : "")));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        long minAmount = configurationService.getWalletTransferMinAmount();
        long maxAmount = configurationService.getWalletTransferMaxAmount();

        String minText = String.format("%,d сум", minAmount);
        String maxText = String.format("%,d сум", maxAmount);
        rows.add(List.of(
                createButton(minText, "WALLET_DEPOSIT_MIN"),
                createButton(maxText, "WALLET_DEPOSIT_MAX")));

        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendDepositConfirmation(Long chatId, Long amount) {
        String platformName = sessionService.getUserData(chatId, "walletPlatform");
        String platformUserId = sessionService.getUserData(chatId, "walletPlatformUserId");
        String fullName = sessionService.getUserData(chatId, "walletFullName");
        if (fullName == null || fullName.isEmpty()) {
            fullName = "—";
        }

        boolean isRub = false;
        long rubAmount = 0L;
        if (platformName != null && !platformName.isEmpty()) {
            String nameForLookup = platformName.replace("_", "");
            Optional<Platform> platformOpt = platformRepository.findByName(nameForLookup);
            if (platformOpt.isPresent() && platformOpt.get().getCurrency() == Currency.RUB) {
                isRub = true;
                rubAmount = exchangeRateRepository.findLatest()
                        .map(rate -> BigDecimal.valueOf(amount).multiply(rate.getUzsToRub())
                                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValue())
                        .orElse(0L);
            }
        }

        String messageKey = isRub ? "wallet.message.deposit_confirm_rub" : "wallet.message.deposit_confirm";
        String platformDisplay = platformName != null ? platformName : "";
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        if (isRub) {
            message.setText(String.format(
                    languageSessionService.getTranslation(chatId, messageKey),
                    escapeMarkdown(platformDisplay), escapeMarkdown(fullName), escapeMarkdown(platformUserId != null ? platformUserId : ""), amount, rubAmount));
        } else {
            message.setText(String.format(
                    languageSessionService.getTranslation(chatId, messageKey),
                    escapeMarkdown(platformDisplay), escapeMarkdown(fullName), escapeMarkdown(platformUserId != null ? platformUserId : ""), amount));
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "wallet.button.transfer"),
                        "WALLET_TRANSFER_CONFIRM")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendWithdrawAmountInput(Long chatId) {
        long minAmount = configurationService.getWalletMinWithdrawAmount();
        UserBalance balance = getOrCreateUserBalance(chatId);
        UserWalletQuota quota = walletQuotaRepository.findById(chatId)
                .orElse(UserWalletQuota.builder().chatId(chatId).earnedQuota(0L).usedQuota(0L).build());
        long withdrawable = Math.min(quota.getRemainingQuota(), balance.getWalletBalance());

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.withdraw_enter_amount"),
                minAmount, withdrawable));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String minText = String.format("%,d сум", minAmount);
        rows.add(List.of(createButton(minText, "WALLET_WITHDRAW_MIN")));

        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendWithdrawCardInput(Long chatId, Long amount) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.withdraw_enter_card"),
                amount));

        List<HizmatRequest> recentRequests = requestRepository.findLatestUniqueCardNumbersByChatId(chatId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!recentRequests.isEmpty()) {
            List<InlineKeyboardButton> pastCardButtons = recentRequests.stream()
                    .map(HizmatRequest::getCardNumber)
                    .filter(c -> c != null && !"WALLET".equals(c))
                    .distinct()
                    .limit(2)
                    .map(card -> createButton(card, "WALLET_PAST_CARD:" + card))
                    .collect(Collectors.toList());
            if (!pastCardButtons.isEmpty()) {
                rows.add(pastCardButtons);
            }
        }
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private void sendWithdrawConfirmation(Long chatId, Long amount, String cardNumber) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.withdraw_confirm"),
                escapeMarkdown(cardNumber != null ? cardNumber : ""), amount));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "wallet.button.transfer"),
                        "WALLET_CASHOUT_CONFIRM")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    // ----- REQUEST HANDLERS -----

    public void handleTextInput(Long chatId, String text) {
        String state = sessionService.getUserState(chatId);
        logger.info("Wallet text input from {}: '{}', state={}", chatId, text, state);

        switch (state) {
            case "WALLET_DEPOSIT_ID_INPUT" -> handleDepositId(chatId, text);
            case "WALLET_DEPOSIT_AMOUNT" -> handleDepositAmount(chatId, text);
            case "WALLET_WITHDRAW_AMOUNT" -> handleWithdrawAmount(chatId, text);
            case "WALLET_WITHDRAW_CARD" -> handleWithdrawCard(chatId, text);
            default -> sendWalletMenu(chatId);
        }
    }

    public void handleCallback(Long chatId, String callback) {
        logger.info("Wallet callback from {}: {}", chatId, callback);
        sessionService.clearMessageIds(chatId);

        if (callback.equals("WALLET_TOPUP")) {
            topUpService.startWalletTopUp(chatId);
        } else if (callback.equals("WALLET_DEPOSIT")) {
            sessionService.setUserState(chatId, "WALLET_DEPOSIT_PLATFORM");
            sessionService.addNavigationState(chatId, "WALLET_MENU");
            sendDepositPlatformSelection(chatId);
        } else if (callback.equals("WALLET_WITHDRAW")) {
            long minAmount = configurationService.getWalletMinWithdrawAmount();
            UserBalance balance = getOrCreateUserBalance(chatId);
            if (balance.getWalletBalance() < minAmount) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.insufficient_balance_for_withdraw"),
                        minAmount));
                m.enableMarkdown(true);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(List.of(createNavigationButtons(chatId)));
                m.setReplyMarkup(markup);
                messageSender.sendMessage(m, chatId);
                return;
            }
            sessionService.setUserState(chatId, "WALLET_WITHDRAW_AMOUNT");
            sessionService.addNavigationState(chatId, "WALLET_MENU");
            sendWithdrawAmountInput(chatId);
        } else if (callback.startsWith("WALLET_PLATFORM:")) {
            String platform = callback.split(":")[1];
            sessionService.setUserData(chatId, "walletPlatform", platform);
            sessionService.setUserState(chatId, "WALLET_DEPOSIT_ID_INPUT");
            sessionService.addNavigationState(chatId, "WALLET_DEPOSIT_PLATFORM");
            sendDepositIdInput(chatId, platform);
        } else if (callback.startsWith("WALLET_PAST_ID:")) {
            String id = callback.split(":")[1];
            handleDepositId(chatId, id);
        } else if (callback.equals("WALLET_DEPOSIT_MIN")) {
            long minAmount = configurationService.getWalletTransferMinAmount();
            handleDepositAmount(chatId, String.valueOf(minAmount));
        } else if (callback.equals("WALLET_DEPOSIT_MAX")) {
            long maxAmount = configurationService.getWalletTransferMaxAmount();
            handleDepositAmount(chatId, String.valueOf(maxAmount));
        } else if (callback.equals("WALLET_WITHDRAW_MIN")) {
            long minAmount = configurationService.getWalletMinWithdrawAmount();
            handleWithdrawAmount(chatId, String.valueOf(minAmount));
        } else if (callback.startsWith("WALLET_PAST_CARD:")) {
            String cardNumber = callback.split(":")[1];
            handleWithdrawCard(chatId, cardNumber);
        } else if (callback.equals("WALLET_TRANSFER_CONFIRM")) {
            processWalletTransfer(chatId);
        } else if (callback.equals("WALLET_CASHOUT_CONFIRM")) {
            processWalletCashout(chatId);
        } else if (callback.startsWith("WALLET_CANCEL:")) {
            Long requestId = Long.parseLong(callback.split(":")[1]);
            self.handleUserCancel(chatId, requestId);
        } else if (callback.startsWith("WALLET_HISTORY:")) {
            int page = Integer.parseInt(callback.split(":")[1]);
            sessionService.setUserState(chatId, "WALLET_HISTORY");
            sessionService.addNavigationState(chatId, "WALLET_MENU");
            sendWalletHistory(chatId, page);
        }
    }

    /**
     * Handles BACK button. Returns true if the caller (bot) should send the payment main menu; false otherwise.
     */
    public boolean handleBack(Long chatId) {
        String lastState = sessionService.popNavigationState(chatId);
        if (lastState == null) {
            sendWalletMenu(chatId);
            return false;
        }

        switch (lastState) {
            case "MAIN_MENU" -> {
                sessionService.setUserState(chatId, "MAIN_MENU");
                sessionService.setUserData(chatId, "returnToMainMenu", "true");
                return true;
            }
            case "WALLET_MENU" -> {
                sessionService.setUserState(chatId, "WALLET_MENU");
                sendWalletMenu(chatId);
            }
            case "WALLET_DEPOSIT_PLATFORM" -> {
                sessionService.setUserState(chatId, "WALLET_DEPOSIT_PLATFORM");
                sendDepositPlatformSelection(chatId);
            }
            case "WALLET_DEPOSIT_ID_INPUT" -> {
                sessionService.setUserState(chatId, "WALLET_DEPOSIT_ID_INPUT");
                sendDepositIdInput(chatId, sessionService.getUserData(chatId, "walletPlatform"));
            }
            case "WALLET_DEPOSIT_AMOUNT" -> {
                sessionService.setUserState(chatId, "WALLET_DEPOSIT_AMOUNT");
                sendDepositAmountInput(chatId);
            }
            case "WALLET_WITHDRAW_AMOUNT" -> {
                sessionService.setUserState(chatId, "WALLET_WITHDRAW_AMOUNT");
                sendWithdrawAmountInput(chatId);
            }
            case "WALLET_WITHDRAW_CARD" -> {
                sessionService.setUserState(chatId, "WALLET_WITHDRAW_CARD");
                String amountStr = sessionService.getUserData(chatId, "walletWithdrawAmount");
                if (amountStr != null) {
                    try {
                        sendWithdrawCardInput(chatId, Long.parseLong(amountStr));
                    } catch (NumberFormatException e) {
                        sendWithdrawAmountInput(chatId);
                    }
                } else {
                    sendWithdrawAmountInput(chatId);
                }
            }
            case "WALLET_HISTORY" -> {
                sessionService.setUserState(chatId, "WALLET_MENU");
                sendWalletMenu(chatId);
            }
            default -> sendWalletMenu(chatId);
        }
        return false;
    }

    // ----- LOGIC IMPLEMENTATIONS -----

    private void handleDepositId(Long chatId, String platformUserId) {
        if (platformUserId == null || !platformUserId.trim().matches("\\d+")) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.invalid_id"));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            return;
        }

        String platform = sessionService.getUserData(chatId, "walletPlatform");
        if (platform == null) {
            sendWalletMenu(chatId);
            return;
        }

        WalletUserIdValidationResult validation = topUpService.validatePlatformUserIdForWallet(chatId, platform, platformUserId.trim());
        if (!validation.isValid()) {
            String errorKey = "topup.message.no_user_found".equals(validation.getErrorMessageKey())
                    ? "wallet.message.platform_user_not_found"
                    : validation.getErrorMessageKey();
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, errorKey));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            sendDepositIdInput(chatId, platform);
            return;
        }

        long minAmount = configurationService.getWalletTransferMinAmount();
        UserBalance balance = getOrCreateUserBalance(chatId);
        if (balance.getWalletBalance() < minAmount) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.insufficient_balance_for_transfer"),
                    minAmount));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            sendDepositIdInput(chatId, platform);
            return;
        }

        sessionService.setUserData(chatId, "walletPlatformUserId", platformUserId.trim());
        String fullName = validation.getFullName();
        sessionService.setUserData(chatId, "walletFullName", fullName != null && !fullName.isEmpty() ? fullName : "");

        sessionService.setUserState(chatId, "WALLET_DEPOSIT_AMOUNT");
        sessionService.addNavigationState(chatId, "WALLET_DEPOSIT_ID_INPUT");
        sendDepositAmountInput(chatId);
    }

    private void handleDepositAmount(Long chatId, String amountStr) {
        try {
            // Guard: ensure required session data still exists (stale inline button protection)
            String platform = sessionService.getUserData(chatId, "walletPlatform");
            String platformUserId = sessionService.getUserData(chatId, "walletPlatformUserId");
            if (platform == null || platform.isEmpty() || platformUserId == null || platformUserId.isEmpty()) {
                sendWalletMenu(chatId);
                return;
            }

            Long amount = Long.parseLong(amountStr.replaceAll("\\s+", ""));
            UserBalance balance = getOrCreateUserBalance(chatId);

            if (amount <= 0) {
                throw new NumberFormatException();
            }
            long minAmount = configurationService.getWalletTransferMinAmount();
            long maxAmount = configurationService.getWalletTransferMaxAmount();
            if (amount < minAmount) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.transfer_min_error"),
                        minAmount));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }
            if (maxAmount > 0 && amount > maxAmount) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.transfer_max_error"),
                        maxAmount));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }
            if (amount > balance.getWalletBalance()) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.insufficient_funds"),
                        balance.getWalletBalance()));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }

            sessionService.setUserData(chatId, "walletDepositAmount", amount.toString());
            sessionService.setUserState(chatId, "WALLET_DEPOSIT_CONFIRM");
            sessionService.addNavigationState(chatId, "WALLET_DEPOSIT_AMOUNT");
            sendDepositConfirmation(chatId, amount);

        } catch (NumberFormatException e) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.invalid_amount"));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
        }
    }

    private void handleWithdrawAmount(Long chatId, String amountStr) {
        try {
            Long amount = Long.parseLong(amountStr.replaceAll("\\s+", ""));
            Long minAmount = configurationService.getWalletMinWithdrawAmount();
            Long ratio = configurationService.getWalletWithdrawRatio();
            UserBalance balance = getOrCreateUserBalance(chatId);

            if (amount < minAmount) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.withdraw_min_error"),
                        minAmount));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }
            if (amount > balance.getWalletBalance()) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.insufficient_funds"),
                        balance.getWalletBalance()));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }

            // Check withdrawal quota
            UserWalletQuota quota = walletQuotaRepository.findById(chatId)
                    .orElse(UserWalletQuota.builder().chatId(chatId).earnedQuota(0L).usedQuota(0L).bonusQuota(0L).build());

            if (quota.getRemainingQuota() == 0L) {
                // No quota (earned or bonus) — blocked
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.withdraw_quota_blocked"),
                        1000L, ratio * 1000));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }

            long remaining = quota.getRemainingQuota();
            if (amount > remaining) {
                SendMessage m = new SendMessage();
                m.setChatId(chatId.toString());
                m.setText(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.withdraw_quota_exceeded"),
                        remaining, 1000L, ratio * 1000));
                m.enableMarkdown(true);
                m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
                messageSender.sendMessage(m, chatId);
                return;
            }

            sessionService.setUserData(chatId, "walletWithdrawAmount", amount.toString());
            sessionService.setUserState(chatId, "WALLET_WITHDRAW_CARD");
            sessionService.addNavigationState(chatId, "WALLET_WITHDRAW_AMOUNT");
            sendWithdrawCardInput(chatId, amount);

        } catch (NumberFormatException e) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.invalid_amount"));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
        }
    }

    private void handleWithdrawCard(Long chatId, String cardStr) {
        String card = cardStr.replaceAll("\\s+", "");
        if (!card.matches("\\d{16}")) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.invalid_card"));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            return;
        }

        sessionService.setUserData(chatId, "walletWithdrawCard", card);
        sessionService.setUserState(chatId, "WALLET_WITHDRAW_CONFIRM");
        sessionService.addNavigationState(chatId, "WALLET_WITHDRAW_CARD");

        Long amount = Long.parseLong(sessionService.getUserData(chatId, "walletWithdrawAmount"));
        sendWithdrawConfirmation(chatId, amount, card);
    }

    // ----- ACTUAL PROCESS FLOWS -----

    public void processWalletTransfer(Long chatId) {
        String amountStr = sessionService.getUserData(chatId, "walletDepositAmount");
        if (amountStr == null)
            return;
        Long amount = Long.parseLong(amountStr);
        long minAmount = configurationService.getWalletTransferMinAmount();
        long maxAmount = configurationService.getWalletTransferMaxAmount();
        if (amount < minAmount) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.transfer_min_error"),
                    minAmount));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            sendPaymentMainMenu(chatId, true);
            return;
        }
        if (maxAmount > 0 && amount > maxAmount) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.transfer_max_error"),
                    maxAmount));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            sendPaymentMainMenu(chatId, true);
            return;
        }
        String platformStr = sessionService.getUserData(chatId, "walletPlatform");
        String platformUserId = sessionService.getUserData(chatId, "walletPlatformUserId");
        String fullName = sessionService.getUserData(chatId, "walletFullName");
        if (fullName == null || fullName.isEmpty())
            fullName = "WALLET";

        sessionService.removeUserData(chatId, "walletDepositAmount");

        HizmatRequest request = self.executeWalletTransferDeduction(chatId, amount, platformStr, platformUserId,
                fullName);
        if (request == null)
            return;

        Platform platform = platformRepository.findByName(platformStr).orElse(null);
        Object transferResult = null;
        try {
            if (platform != null && "mostbet".equals(platform.getType())) {
                transferResult = mostbetService.transferToPlatform(request);
            } else {
                transferResult = topUpService.transferToPlatform(request, "WALLET");
            }
        } catch (Exception e) {
            logger.error("Platform API transfer failed for wallet transfer: {}", e.getMessage());
            transferResult = null;
        }

        self.finalizeWalletTransfer(chatId, request.getId(), transferResult != null, amount, platformStr,
                platformUserId, transferResult);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public HizmatRequest executeWalletTransferDeduction(Long chatId, Long amount, String platformStr,
            String platformUserId, String fullName) {
        UserBalance balance = userBalanceRepository.findByIdWithLock(chatId)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        long current = balance.getWalletBalance() != null ? balance.getWalletBalance() : 0L;
        if (current < amount) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.insufficient_funds"),
                    current));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            return null;
        }

        balance.setWalletBalance(current - amount);
        userBalanceRepository.save(balance);

        HizmatRequest request = new HizmatRequest();
        request.setChatId(chatId);
        request.setAmount(amount);
        request.setUniqueAmount(amount);
        request.setPlatform(platformStr);
        request.setPlatformUserId(platformUserId);
        request.setFullName(fullName);
        request.setType(RequestType.WALLET_TO_PLATFORM);
        request.setStatus(RequestStatus.PENDING);
        request.setCurrency(platformRepository.findByName(platformStr).map(Platform::getCurrency).orElse(Currency.UZS));
        request.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        return requestRepository.save(request);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void finalizeWalletTransfer(Long chatId, Long requestId, boolean success, Long amount, String platformStr,
            String platformUserId, Object transferResult) {
        HizmatRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null)
            return;

        if (success) {
            request.setStatus(RequestStatus.APPROVED);
            long walletBalanceAtTime = userBalanceRepository.findById(chatId)
                    .map(ub -> ub.getWalletBalance() != null ? ub.getWalletBalance() : 0L)
                    .orElse(0L);
            request.setWalletBalanceAtTime(walletBalanceAtTime);
            requestRepository.save(request);

            String userLog = String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.transfer_success"),
                    request.getId(), escapeMarkdown(platformStr), escapeMarkdown(platformUserId), amount);
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(userLog);
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);

            // Same as card-to-platform: award tickets, credit referral
            long ticketsAwarded = 0L;
            long ticketCalculationAmount = configurationService.getTicketCalculationAmount();
            if (ticketCalculationAmount > 0) {
                ticketsAwarded = amount / ticketCalculationAmount;
                if (ticketsAwarded > 0) {
                    lotteryService.awardTickets(chatId, ticketsAwarded);
                }
            }
            bonusService.creditReferral(chatId, amount);

            // Quota earned only for wallet-to-platform; never for card-to-wallet top-ups.
            Long ratio = configurationService.getWalletWithdrawRatio();
            long earned = amount * ratio;
            UserWalletQuota quota = walletQuotaRepository.findByIdWithLock(chatId)
                    .orElse(UserWalletQuota.builder().chatId(chatId).earnedQuota(0L).usedQuota(0L).build());
            quota.setEarnedQuota(quota.getEarnedQuota() + earned);
            walletQuotaRepository.save(quota);
            logger.info("Quota earned for chatId {}: +{} (ratio={}), total earned={}, remaining={}",
                    chatId, earned, ratio, quota.getEarnedQuota(), quota.getRemainingQuota());

            var balanceOpt = userBalanceRepository.findById(chatId);
            long walletLeft = balanceOpt
                    .map(ub -> ub.getWalletBalance() != null ? ub.getWalletBalance() : 0L)
                    .orElse(0L);
            long platformBalanceUzs = 0L;
            if (transferResult instanceof BalanceLimit) {
                BalanceLimit bl = (BalanceLimit) transferResult;
                if (bl.getLimit() != null)
                    platformBalanceUzs = bl.getLimit().longValue();
                else if (bl.getBalance() != null)
                    platformBalanceUzs = bl.getBalance().longValue();
            }
            long ticketsTotal = balanceOpt.map(ub -> ub.getTickets() != null ? ub.getTickets() : 0L).orElse(0L);
            String dateStr = LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String adminLog = String.format(
                    "✅ *Hamyondan kontoraga o'tkazma*\n\n🆔: `%d`\n👤: `%d`\n🌐 #%s: %s\n💸 Summa: `%,d UZS`\n🔰 Yechib olish kvotasi: `+%,d UZS`\n🎟️ Chiptalar: %d (+ %d)\n🏧 Qoldi: `%,d UZS`\n\n🏦: %,d UZS\n\n📅 %s",
                    request.getId(), chatId, escapeMarkdown(platformStr), escapeMarkdown(platformUserId), amount, earned, ticketsTotal, ticketsAwarded, walletLeft, platformBalanceUzs, dateStr);
            adminLogBotService.sendLog(adminLog);
        } else {
            // Transfer reported as failed - do NOT refund. Platform may process with delay;
            // auto-refund would cause double-credit if user receives on platform later.
            request.setStatus(RequestStatus.FAILED);
            requestRepository.save(request);

            SendMessage failMsg = new SendMessage();
            failMsg.setChatId(chatId.toString());
            failMsg.setText(languageSessionService.getTranslation(chatId, "wallet.message.transfer_failed_no_refund"));
            failMsg.enableMarkdown(true);
            failMsg.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(failMsg, chatId);

            String userDetailMsg = String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.transfer_failed_detail_no_refund"),
                    request.getId(), chatId, escapeMarkdown(platformStr), escapeMarkdown(platformUserId), amount);
            SendMessage detailMsg = new SendMessage();
            detailMsg.setChatId(chatId.toString());
            detailMsg.setText(userDetailMsg);
            detailMsg.enableMarkdown(true);
            detailMsg.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(detailMsg, chatId);

            String adminLog = String.format(
                    "❌ Wallet to Platform transfer FAILED (choose Refund or No refund):\n🆔 ID: `%d`\n👤 User: `%d`\n🌐 Platform: %s\n📋 Platform ID: `%s`\n💸 Amount: %,d UZS",
                    request.getId(), chatId, escapeMarkdown(platformStr), escapeMarkdown(platformUserId), amount);
            adminLogBotService.sendToAdmins(adminLog, adminLogBotService.createWalletFailRefundKeyboard(request.getId()));
        }

        sendPaymentMainMenu(chatId, true);
    }

    public void processWalletCashout(Long chatId) {
        String amountStr = sessionService.getUserData(chatId, "walletWithdrawAmount");
        if (amountStr == null)
            return;
        Long amount = Long.parseLong(amountStr);
        String card = sessionService.getUserData(chatId, "walletWithdrawCard");

        sessionService.removeUserData(chatId, "walletWithdrawAmount");
        sessionService.removeUserData(chatId, "walletWithdrawCard");

        HizmatRequest request = self.executeWalletCashoutDeduction(chatId, amount, card);
        if (request == null)
            return;

        // User pending message with Cancel button
        String pendingDateStr = request.getCreatedAt() != null
                ? request.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.withdraw_pending"),
                request.getId(), amount, escapeMarkdown(card), pendingDateStr));
        message.enableMarkdown(true);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "wallet.button.cancel"),
                "WALLET_CANCEL:" + request.getId())));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        messageSender.sendMessage(message, chatId);

        // Admin Notification (ID and user copyable via backticks; date for reference)
        String adminDateStr = request.getCreatedAt() != null
                ? request.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String adminMsg = String.format(
                "#Yechish so'rovi\n\n🆔: `%d`\n👤: `%d`\n💵 Summa: `%,d UZS`\n📅 %s",
                request.getId(), chatId, amount, adminDateStr);

        InlineKeyboardMarkup adminMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> adminRows = new ArrayList<>();
        adminRows.add(List.of(
                createButton("⏳ Qabul qilish", "WALLET_ADMIN_TAKE:" + request.getId()),
                createButton("❌ Rad etish", "WALLET_ADMIN_DECLINE:" + request.getId())));
        adminMarkup.setKeyboard(adminRows);

        adminLogBotService.sendToAdmins(adminMsg, adminMarkup);

        // Go back to payment main menu
        sendPaymentMainMenu(chatId, true);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public HizmatRequest executeWalletCashoutDeduction(Long chatId, Long amount, String card) {
        UserBalance balance = userBalanceRepository.findByIdWithLock(chatId)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        long current = balance.getWalletBalance() != null ? balance.getWalletBalance() : 0L;
        if (current < amount) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(String.format(
                    languageSessionService.getTranslation(chatId, "wallet.message.insufficient_funds"),
                    current));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            return null;
        }

        balance.setWalletBalance(current - amount);
        userBalanceRepository.save(balance);

        HizmatRequest request = new HizmatRequest();
        request.setChatId(chatId);
        request.setAmount(amount);
        request.setUniqueAmount(amount);
        request.setCardNumber(card);
        request.setFullName("WALLET");
        request.setPlatform("Wallet");
        request.setCurrency(Currency.UZS);
        request.setType(RequestType.WALLET_WITHDRAWAL);
        request.setStatus(RequestStatus.PENDING_ADMIN);
        request.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        request.setWalletBalanceAtTime(balance.getWalletBalance() != null ? balance.getWalletBalance() : 0L);
        request = requestRepository.save(request);

        // Decrease quota immediately when request is sent to admin (not on approval)
        walletQuotaRepository.findByIdWithLock(chatId).ifPresent(quota -> {
            long totalAvailable = quota.getEarnedQuota() + (quota.getBonusQuota() != null ? quota.getBonusQuota() : 0L);
            long newUsed = Math.min(quota.getUsedQuota() + amount, totalAvailable);
            quota.setUsedQuota(newUsed);
            walletQuotaRepository.save(quota);
            logger.info("Quota used for chatId {} at submit: +{}, total used={}, remaining={}",
                    chatId, amount, newUsed, quota.getRemainingQuota());
        });

        return request;
    }

    // ----- ADMIN AND CANCEL ACTIONS -----

    @Transactional
    public void handleAdminTake(Long requestId, Long adminChatId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.PENDING_ADMIN
                || request.getType() != RequestType.WALLET_WITHDRAWAL) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "❌ So'rov topilmadi yoki allaqachon ko'rib chiqilgan: 🆔 " + requestId);
            return;
        }

        // Lock it so user cannot cancel
        request.setStatus(RequestStatus.PROCESSING);
        requestRepository.save(request);

        // Send full details to the specific admin who took it (markdown backticks = copyable)
        String phone = blockedUserRepository.findByChatId(request.getChatId()).map(BlockedUser::getPhoneNumber).orElse("-");
        String dateStr = request.getCreatedAt() != null
                ? request.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String cardNum = request.getCardNumber() != null ? request.getCardNumber() : "-";
        String adminMsg = String.format(
                "#Pul yechish so'rovi 💸\n\n🆔: `%d`\n👤: `%d`\n📞: `%s`\n💳 Karta: `%s`\n💵 Summa: %,d UZS\n📅 %s",
                request.getId(), request.getChatId(), phone, cardNum, request.getAmount(), dateStr);

        InlineKeyboardMarkup adminMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> adminRows = new ArrayList<>();
        adminRows.add(List.of(
                createButton("✅ Bajarildi", "WALLET_ADMIN_CONFIRM:" + request.getId()),
                createButton("❌ Bekor qilish", "WALLET_ADMIN_DECLINE:" + request.getId())));
        adminMarkup.setKeyboard(adminRows);

        adminLogBotService.sendToSingleAdmin(adminChatId, adminMsg, adminMarkup);
    }

    @Transactional
    public void handleAdminConfirm(Long requestId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId).orElse(null);
        if (request == null
                || (request.getStatus() != RequestStatus.PENDING_ADMIN
                        && request.getStatus() != RequestStatus.PROCESSING)
                || request.getType() != RequestType.WALLET_WITHDRAWAL) {
            adminLogBotService.sendToAdmins("❌ Request not found or already processed: 🆔 " + requestId);
            return;
        }

        // Mark as approved
        request.setStatus(RequestStatus.APPROVED);
        long walletLeft = userBalanceRepository.findById(request.getChatId())
                .map(ub -> ub.getWalletBalance() != null ? ub.getWalletBalance() : 0L)
                .orElse(0L);
        request.setWalletBalanceAtTime(walletLeft);
        requestRepository.save(request);

        // Quota was already decreased when request was sent to admin; nothing to do here

        // Notify Admins (Uzbek, with emojis and wallet balance left)
        String confirmDateStr = LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String adminMsg = String.format(
                "✅ *Hamyondan kartaga yechish tasdiqlandi*\n\n🆔: `%d`\n👤: `%d`\n💸 Summa: `%,d UZS`\n💳 Karta: `%s`\n🏧 Qoldi: `%,d UZS`\n📅 %s",
                request.getId(), request.getChatId(), request.getAmount(), escapeMarkdown(request.getCardNumber()), walletLeft, confirmDateStr);
        adminLogBotService.sendToAdmins(adminMsg);

        // Notify User
        SendMessage m = new SendMessage();
        m.setChatId(request.getChatId().toString());
        m.setText(String.format(
                languageSessionService.getTranslation(request.getChatId(), "wallet.message.withdraw_admin_confirmed"),
                request.getId(), request.getAmount(), escapeMarkdown(request.getCardNumber())));
        m.enableMarkdown(true);
        m.setReplyMarkup(createMainMenuOnlyMarkup(request.getChatId()));
        messageSender.sendMessage(m, request.getChatId());
    }

    @Transactional
    public void handleAdminDecline(Long requestId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId).orElse(null);
        if (request == null
                || (request.getStatus() != RequestStatus.PENDING_ADMIN
                        && request.getStatus() != RequestStatus.PROCESSING)
                || request.getType() != RequestType.WALLET_WITHDRAWAL) {
            adminLogBotService.sendToAdmins("❌ Request not found or already processed: 🆔 " + requestId);
            return;
        }

        // Return funds
        UserBalance balance = userBalanceRepository.findByIdWithLock(request.getChatId()).orElse(null);
        if (balance != null) {
            balance.setWalletBalance(balance.getWalletBalance() + request.getAmount());
            userBalanceRepository.save(balance);
        }

        // Return quota (was decreased when request was sent to admin)
        walletQuotaRepository.findByIdWithLock(request.getChatId()).ifPresent(quota -> {
            long newUsed = Math.max(0L, quota.getUsedQuota() - request.getAmount());
            quota.setUsedQuota(newUsed);
            walletQuotaRepository.save(quota);
            logger.info("Quota returned on decline for chatId {}: -{}, total used={}, remaining={}",
                    request.getChatId(), request.getAmount(), newUsed, quota.getRemainingQuota());
        });

        // Mark as declined/canceled
        request.setStatus(RequestStatus.CANCELED);
        requestRepository.save(request);

        // Notify Admins
        String adminMsg = String.format("❌ Wallet Withdrawal Declined: 🆔 %d", request.getId());
        adminLogBotService.sendToAdmins(adminMsg);

        // Notify User
        SendMessage m = new SendMessage();
        m.setChatId(request.getChatId().toString());
        m.setText(String.format(
                languageSessionService.getTranslation(request.getChatId(), "wallet.message.withdraw_admin_declined"),
                request.getId()));
        m.enableMarkdown(true);
        m.setReplyMarkup(createMainMenuOnlyMarkup(request.getChatId()));
        messageSender.sendMessage(m, request.getChatId());
    }

    /**
     * Admin chose "Refund" for a failed wallet-to-platform transfer. Credit the user's wallet.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleAdminRefundWalletFail(Long requestId, Long adminChatId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId).orElse(null);
        if (request == null || request.getType() != RequestType.WALLET_TO_PLATFORM) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "❌ So'rov topilmadi yoki bu hamyon→kontora o'tkazmasi emas: 🆔 " + requestId);
            return;
        }
        if (request.getStatus() == RequestStatus.FAILED_REFUNDED) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "⚠️ Bu so'rov uchun hamyon allaqachon qaytarilgan: 🆔 " + requestId);
            return;
        }
        if (request.getStatus() != RequestStatus.FAILED) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "❌ So'rov holati refund uchun mos emas: 🆔 " + requestId);
            return;
        }
        Long amount = request.getAmount() != null ? request.getAmount() : 0L;
        if (amount <= 0) {
            adminLogBotService.sendToSingleAdmin(adminChatId, "❌ Summa noto'g'ri: 🆔 " + requestId);
            return;
        }
        UserBalance balance = userBalanceRepository.findByIdWithLock(request.getChatId()).orElse(null);
        if (balance != null) {
            balance.setWalletBalance(balance.getWalletBalance() + amount);
            userBalanceRepository.save(balance);
        }
        request.setStatus(RequestStatus.FAILED_REFUNDED);
        requestRepository.save(request);

        String adminMsg = String.format(
                "✅ *Refund qilindi*\n🆔 ID: `%d`\n👤 User: `%d`\n💸 Summa: %,d UZS — hamyonga qaytarildi.",
                requestId, request.getChatId(), amount);
        adminLogBotService.sendToSingleAdmin(adminChatId, adminMsg);

        SendMessage m = new SendMessage();
        m.setChatId(request.getChatId().toString());
        m.setText(String.format(
                languageSessionService.getTranslation(request.getChatId(), "wallet.message.transfer_failed_refunded_by_admin"),
                requestId, amount));
        m.enableMarkdown(true);
        m.setReplyMarkup(createMainMenuOnlyMarkup(request.getChatId()));
        messageSender.sendMessage(m, request.getChatId());
    }

    /**
     * Admin chose "No refund" for a failed wallet-to-platform transfer (platform likely received).
     * Marks the request APPROVED so the wallet is NOT credited and the "Refund" action can no
     * longer be applied afterwards (prevents a double-credit / money loss).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleAdminNoRefundWalletFail(Long requestId, Long adminChatId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId).orElse(null);
        if (request == null || request.getType() != RequestType.WALLET_TO_PLATFORM) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "❌ So'rov topilmadi yoki bu hamyon→kontora o'tkazmasi emas: 🆔 " + requestId);
            return;
        }
        if (request.getStatus() == RequestStatus.FAILED_REFUNDED) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "⚠️ Bu so'rov uchun hamyon allaqachon qaytarilgan — 'qaytarilmasin' amal qilmaydi: 🆔 " + requestId);
            return;
        }
        if (request.getStatus() != RequestStatus.FAILED) {
            adminLogBotService.sendToSingleAdmin(adminChatId,
                    "⚠️ Bu so'rov allaqachon ko'rib chiqilgan: 🆔 " + requestId);
            return;
        }
        // Treat as completed on the platform: mark APPROVED so no refund can be issued later.
        request.setStatus(RequestStatus.APPROVED);
        requestRepository.save(request);

        String msg = String.format(
                "✔️ *No refund* — kontorada pul tushgan deb qabul qilindi.\n🆔 ID: `%d`\n👤 User: `%d`\n💸 Summa: %,d UZS",
                requestId, request.getChatId(), request.getAmount() != null ? request.getAmount() : 0L);
        adminLogBotService.sendToSingleAdmin(adminChatId, msg);
    }

    @Transactional
    public void handleUserCancel(Long chatId, Long requestId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId).orElse(null);
        if (request == null || request.getChatId().longValue() != chatId.longValue()
                || request.getType() != RequestType.WALLET_WITHDRAWAL) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.withdraw_already_confirmed"));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            return;
        }

        if (request.getStatus() != RequestStatus.PENDING_ADMIN) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.withdraw_already_confirmed"));
            m.enableMarkdown(true);
            m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
            messageSender.sendMessage(m, chatId);
            return;
        }

        // Return funds
        UserBalance balance = userBalanceRepository.findByIdWithLock(chatId).orElse(null);
        if (balance != null) {
            balance.setWalletBalance(balance.getWalletBalance() + request.getAmount());
            userBalanceRepository.save(balance);
        }

        // Return quota (was decreased when request was sent to admin)
        walletQuotaRepository.findByIdWithLock(chatId).ifPresent(quota -> {
            long newUsed = Math.max(0L, quota.getUsedQuota() - request.getAmount());
            quota.setUsedQuota(newUsed);
            walletQuotaRepository.save(quota);
            logger.info("Quota returned on user cancel for chatId {}: -{}, total used={}, remaining={}",
                    chatId, request.getAmount(), newUsed, quota.getRemainingQuota());
        });

        // Mark as canceled
        request.setStatus(RequestStatus.USER_CANCELED);
        requestRepository.save(request);

        // Notify User
        SendMessage m = new SendMessage();
        m.setChatId(chatId.toString());
        m.setText(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.withdraw_user_canceled"),
                request.getId()));
        m.enableMarkdown(true);
        m.setReplyMarkup(createMainMenuOnlyMarkup(chatId));
        messageSender.sendMessage(m, chatId);

        // Notify Admins
        String adminMsg = String.format("❌ 👤 User %d canceled Wallet Withdrawal 🆔 %d", chatId, request.getId());
        adminLogBotService.sendToAdmins(adminMsg);
    }

    // ----- UTILS -----

    private UserBalance getOrCreateUserBalance(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId).orElseGet(() -> {
            UserBalance b = UserBalance.builder()
                    .chatId(chatId)
                    .tickets(0L)
                    .balance(BigDecimal.ZERO)
                    .walletBalance(0L)
                    .build();
            return userBalanceRepository.save(b);
        });
        // Legacy rows created before the wallet_balance column may have NULL; normalize to 0
        // so all downstream comparisons are NPE-safe.
        if (balance.getWalletBalance() == null) {
            balance.setWalletBalance(0L);
        }
        return balance;
    }

    private InlineKeyboardButton createButton(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }

    private InlineKeyboardButton createUrlButton(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setUrl(url);
        return button;
    }

    private void sendPaymentMainMenu(Long chatId, boolean clearSession) {
        if (clearSession) {
            sessionService.clearSession(chatId);
        }
        sessionService.setUserState(chatId, "MAIN_MENU");

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(languageSessionService.getTranslation(chatId, "message.main_menu_welcome"));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.topup"), "TOPUP")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.withdraw"), "WITHDRAW")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.bonus"), "BONUS")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.wallet"), "WALLET")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.contact"), "CONTACT")));
        rows.add(List.of(createUrlButton(languageSessionService.getTranslation(chatId, "button.instruction"), "https://t.me/BaronPeyInfo")));
        markup.setKeyboard(rows);

        message.setReplyMarkup(markup);
        messageSender.sendMessage(message, chatId);
    }

    private List<InlineKeyboardButton> createNavigationButtons(Long chatId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "button.back"), "BACK"));
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "button.home"), "HOME"));
        return buttons;
    }

    /** Single row with main menu (HOME) button only. Use for error/info messages. */
    private InlineKeyboardMarkup createMainMenuOnlyMarkup(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(createButton(languageSessionService.getTranslation(chatId, "button.home"), "HOME"))));
        return markup;
    }

    // ----- HISTORY -----

    private static final int HISTORY_PAGE_SIZE = 5;

    /** Only show successful (approved) wallet transactions in history. */
    private static final java.util.List<RequestStatus> WALLET_HISTORY_SUCCESS_STATUSES = java.util.List.of(
            RequestStatus.APPROVED, RequestStatus.BONUS_APPROVED);

    /** For withdrawals only: also show pending and in-progress so user sees them before admin approves. */
    private static final java.util.List<RequestStatus> WALLET_HISTORY_WITHDRAWAL_STATUSES = java.util.List.of(
            RequestStatus.APPROVED, RequestStatus.BONUS_APPROVED, RequestStatus.PENDING_ADMIN, RequestStatus.PROCESSING);

    private void sendWalletHistory(Long chatId, int page) {
        long totalItems = requestRepository.countWalletHistoryByChatId(chatId, WALLET_HISTORY_SUCCESS_STATUSES, WALLET_HISTORY_WITHDRAWAL_STATUSES);

        if (totalItems == 0) {
            SendMessage m = new SendMessage();
            m.setChatId(chatId.toString());
            m.setText(languageSessionService.getTranslation(chatId, "wallet.message.history_empty"));
            m.enableMarkdown(true);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(createNavigationButtons(chatId)));
            m.setReplyMarkup(markup);
            messageSender.sendMessage(m, chatId);
            return;
        }

        int totalPages = (int) Math.ceil((double) totalItems / HISTORY_PAGE_SIZE);
        if (page < 0)
            page = 0;
        if (page >= totalPages)
            page = totalPages - 1;

        org.springframework.data.domain.Page<HizmatRequest> historyPage = requestRepository
                .findWalletHistoryByChatId(chatId, WALLET_HISTORY_SUCCESS_STATUSES, WALLET_HISTORY_WITHDRAWAL_STATUSES,
                        org.springframework.data.domain.PageRequest.of(page, HISTORY_PAGE_SIZE,
                                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                languageSessionService.getTranslation(chatId, "wallet.message.history_title"),
                page + 1, totalPages));

        for (HizmatRequest req : historyPage.getContent()) {
            sb.append("\t");
            String date = req.getCreatedAt() != null ? req.getCreatedAt().format(dtf) : "-";
            String status = formatStatusWithLabel(chatId, req.getStatus());
            String maskedCard = req.getCardNumber() != null && req.getCardNumber().length() >= 4
                    ? "****" + req.getCardNumber().substring(req.getCardNumber().length() - 4)
                    : "-";
            long amount = req.getAmount() != null ? req.getAmount() : 0L;

            switch (req.getType()) {
                case TOP_UP -> sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.history_item_deposit"),
                        req.getId(), amount, escapeMarkdown(maskedCard), escapeMarkdown(date), escapeMarkdown(status)));
                case WALLET_WITHDRAWAL -> sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.history_item_withdrawal"),
                        req.getId(), amount, escapeMarkdown(maskedCard), escapeMarkdown(date), escapeMarkdown(status)));
                case WALLET_TO_PLATFORM -> sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.history_item_platform"),
                        req.getId(),
                        escapeMarkdown(req.getPlatform() != null ? req.getPlatform() : "-"),
                        escapeMarkdown(req.getPlatformUserId() != null ? req.getPlatformUserId() : "-"),
                        amount, escapeMarkdown(date), escapeMarkdown(status)));
                case WITHDRAWAL -> sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.history_item_platform_to_wallet"),
                        req.getId(),
                        escapeMarkdown(req.getPlatform() != null ? req.getPlatform() : "-"),
                        escapeMarkdown(req.getPlatformUserId() != null ? req.getPlatformUserId() : "-"),
                        req.getUniqueAmount() != null ? req.getUniqueAmount() : amount,
                        escapeMarkdown(date), escapeMarkdown(status)));
                default -> {
                }
            }
            Long balanceAtTime = req.getWalletBalanceAtTime();
            if (balanceAtTime != null) {
                sb.append(String.format(
                        languageSessionService.getTranslation(chatId, "wallet.message.history_balance_at_time"),
                        balanceAtTime));
            } else {
                sb.append(languageSessionService.getTranslation(chatId, "wallet.message.history_balance_na"));
            }
            sb.append("\n\n");
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(sb.toString());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Pagination row: ◀️ page / total ▶️
        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        if (page > 0) {
            paginationRow.add(createButton("◀️", "WALLET_HISTORY:" + (page - 1)));
        }
        paginationRow.add(createButton(
                String.format(languageSessionService.getTranslation(chatId, "wallet.message.history_page"),
                        page + 1, totalPages),
                "NOOP"));
        if (page < totalPages - 1) {
            paginationRow.add(createButton("▶️", "WALLET_HISTORY:" + (page + 1)));
        }
        rows.add(paginationRow);

        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        message.enableMarkdown(true);

        messageSender.sendMessage(message, chatId);
    }

    private String formatStatus(RequestStatus status) {
        return switch (status) {
            case APPROVED, BONUS_APPROVED -> "✅";
            case PENDING, PENDING_SMS, PENDING_ADMIN, PENDING_PAYMENT, PENDING_SCREENSHOT, PROCESSING -> "⏳";
            case CANCELED, USER_CANCELED -> "❌";
            case FAILED, FAILED_REFUNDED -> "💥";
        };
    }

    /** Returns status emoji + translated label for history (e.g. "✅ Tasdiqlandi"). */
    private String formatStatusWithLabel(Long chatId, RequestStatus status) {
        String emoji = formatStatus(status);
        String key = switch (status) {
            case APPROVED, BONUS_APPROVED -> "wallet.message.history_status_approved";
            case PENDING, PENDING_SMS, PENDING_ADMIN, PENDING_PAYMENT, PENDING_SCREENSHOT, PROCESSING -> "wallet.message.history_status_pending";
            case CANCELED, USER_CANCELED -> "wallet.message.history_status_canceled";
            case FAILED, FAILED_REFUNDED -> "wallet.message.history_status_failed";
        };
        String label = languageSessionService.getTranslation(chatId, key);
        return emoji + " " + label;
    }

    private String escapeMarkdown(String text) {
        if (text == null)
            return "";
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[");
    }
}
