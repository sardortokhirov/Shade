package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.dto.BalanceLimit;
import com.example.shade.model.*;
import com.example.shade.model.Currency;
import com.example.shade.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.xml.bind.DatatypeConverter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import com.example.shade.repository.*;
import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BonusService {
    private static final Logger logger = LoggerFactory.getLogger(BonusService.class);
    private final UserSessionService sessionService;
    private final ReferralRepository referralRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final PlatformRepository platformRepository;
    private final HizmatRequestRepository requestRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final BlockedUserService blockedUserService;
    private final AdminChatRepository adminChatRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final PromoWhitelistService promoWhitelistService;
    private final LotteryService lotteryService;
    private final MessageSender messageSender;
    private final AdminLogBotService adminLogBotService;
    private final MostbetService mostbetService;
    private final LanguageSessionService languageSessionService; // Injected bean
    private final RestTemplate restTemplate = new RestTemplate();
    private final SystemConfigurationService configurationService;
    private final DailyStatsService dailyStatsService;
    private final FeatureService featureService;
    private final UserPlatformPermissionRepository permissionRepository;
    private final LotteryConfigService lotteryConfigService;
    private final LotteryTicketBundleService bundleService;
    private final LotteryTicketPurchaseService purchaseService;
    private final LottoBotService lottoBotService;
    private final UserLimitIncreaseService userLimitIncreaseService;
    private final DailyUserStatsRepository dailyUserStatsRepository;

    @Lazy
    @Autowired
    private BonusService bonusServiceProxy;

    @PersistenceContext
    private EntityManager entityManager;

    public void startBonus(Long chatId) {
        logger.info("Starting bonus section for chatId: {}", chatId);

        // Usage of promo logic moved to validation
        sessionService.setUserState(chatId, "BONUS_MENU");
        sessionService.addNavigationState(chatId, "MAIN_MENU");
        sendBonusMenu(chatId);
    }

    public void handleCallback(Long chatId, String callback) throws Exception {
        logger.info("Bonus callback for chatId {}: {}", chatId, callback);
        // messageSender.animateAndDeleteMessages(chatId,
        // sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (callback.startsWith("BONUS_TOPUP_PLATFORM:")) {
            String platformName = callback.split(":")[1];
            sessionService.setUserData(chatId, "platform", platformName);
            sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
            sessionService.addNavigationState(chatId, "BONUS_TOPUP");
            sendUserIdInput(chatId, platformName);
            return;
        }
        if (callback.startsWith("BONUS_TOPUP_PAST_ID:")) {
            String userId = callback.split(":")[1];
            validateUserId(chatId, userId);
            return;
        }
        if ("BONUS_TOPUP_APPROVE_USER".equals(callback)) {
            handleApproveUser(chatId);
            return;
        }
        if ("BONUS_TOPUP_REJECT_USER".equals(callback)) {
            sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
            sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            return;
        }
        if ("BONUS_TOPUP_CONFIRM_YES".equals(callback)) {
            bonusServiceProxy.initiateTopUpRequest(chatId);
            return;
        }
        if ("BONUS_TOPUP_CONFIRM_NO".equals(callback)) {
            sessionService.setUserState(chatId, "BONUS_TOPUP");
            sendTopUpPlatformMenu(chatId);
            return;
        }
        if ("BONUS_TOPUP_AMOUNT_MIN".equals(callback)) {
            BigDecimal minAmount = configurationService.getBonusTopUpMinAmount();
            handleTopUpInput(chatId, minAmount.toPlainString());
            return;
        }
        if ("BONUS_TOPUP_AMOUNT_MAX".equals(callback)) {
            BigDecimal maxAmount = configurationService.getBonusTopUpMaxAmount();
            handleTopUpInput(chatId, maxAmount.toPlainString());
            return;
        }
        if (callback.startsWith("ADMIN_APPROVE_TRANSFER:")) {
            Long requestId = Long.valueOf(callback.split(":")[1]);
            bonusServiceProxy.handleAdminApproveTransfer(chatId, requestId);
            return;
        }
        if (callback.startsWith("ADMIN_DECLINE_TRANSFER:")) {
            Long requestId = Long.valueOf(callback.split(":")[1]);
            handleAdminDeclineTransfer(chatId, requestId);
            return;
        }
        if (callback.startsWith("ADMIN_DECLINE_REFUND_TRANSFER:")) {
            Long requestId = Long.valueOf(callback.split(":")[1]);
            handleAdminDeclineTransferWithRefund(chatId, requestId);
            return;
        }
        if (callback.startsWith("ADMIN_REMOVE_TICKETS:")) {
            String userChatId = callback.split(":")[1];
            handleAdminRemoveTickets(chatId, Long.parseLong(userChatId));
            return;
        }
        if (callback.startsWith("ADMIN_REMOVE_BONUS:")) {
            String userChatId = callback.split(":")[1];
            handleAdminRemoveBonus(chatId, Long.parseLong(userChatId));
            return;
        }
        if (callback.startsWith("ADMIN_BLOCK_USER:")) {
            String userChatId = callback.split(":")[1];
            handleAdminBlockUser(chatId, Long.parseLong(userChatId));
            return;
        }
        if (callback.startsWith("BONUS_LOTTERY_BUY_BUNDLE:")) {
            Long bundleId = Long.parseLong(callback.split(":")[1]);
            sendBuyTicketsConfirmation(chatId, bundleId);
            return;
        }
        if (callback.startsWith("BONUS_LOTTERY_CONFIRM_BUY:")) {
            Long bundleId = Long.parseLong(callback.split(":")[1]);
            processTicketPurchase(chatId, bundleId);
            return;
        }

        switch (callback) {
            case "BONUS_LOTTERY" -> {
                sessionService.setUserState(chatId, "BONUS_LOTTERY");
                sessionService.addNavigationState(chatId, "BONUS_MENU");
                sendLotteryMenu(chatId);
            }
            case "BONUS_REFERRAL" -> {
                sessionService.setUserState(chatId, "BONUS_REFERRAL");
                sessionService.addNavigationState(chatId, "BONUS_MENU");
                sendReferralMenu(chatId);
            }
            case "BONUS_LOTTERY_PLAY" -> bonusServiceProxy.playLottery(chatId);
            case "BONUS_LOTTERY_BUY" -> {
                sessionService.setUserState(chatId, "BONUS_LOTTERY_BUY");
                sessionService.addNavigationState(chatId, "BONUS_LOTTERY");
                sendBuyTicketsMenu(chatId);
            }
            case "BONUS_REFERRAL_LINK" -> sendReferralLink(chatId);
            case "BONUS_TOPUP" -> {
                String savedPlatform = sessionService.getUserData(chatId, "platform");
                if (savedPlatform != null) {
                    sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
                    sessionService.addNavigationState(chatId, "BONUS_MENU");
                    sendUserIdInput(chatId, savedPlatform);
                } else {
                    sessionService.setUserState(chatId, "BONUS_TOPUP");
                    sessionService.addNavigationState(chatId, "BONUS_MENU");
                    sendTopUpPlatformMenu(chatId);
                }
            }
            default ->
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "message.invalid_command"));
        }
    }

    public void handleTextInput(Long chatId, String text) {
        String state = sessionService.getUserState(chatId);
        logger.info("Text input for bonus, chatId: {}, state: {}, text: {}", chatId, state, text);
        if ("BONUS_TOPUP_USER_ID".equals(state)) {
            handleUserIdInput(chatId, text);
        } else if ("BONUS_TOPUP_INPUT".equals(state)) {
            handleTopUpInput(chatId, text);
        } else {
            backMenuMessage(chatId, languageSessionService.getTranslation(chatId, "message.select_from_menu"));
        }
    }

    public void backMenuMessage(Long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setReplyMarkup(createNavigationKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    public void handleBack(Long chatId) {
        String lastState = sessionService.popNavigationState(chatId);
        logger.info("Handling back for bonus, chatId: {}, lastState: {}", chatId, lastState);
        if (lastState == null) {
            sendMainMenu(chatId);
            return;
        }
        switch (lastState) {
            case "MAIN_MENU" -> sendMainMenu(chatId);
            case "BONUS_MENU" -> {
                sessionService.setUserState(chatId, "BONUS_MENU");
                sendBonusMenu(chatId);
            }
            case "BONUS_LOTTERY" -> {
                sessionService.setUserState(chatId, "BONUS_LOTTERY");
                sendLotteryMenu(chatId);
            }
            case "BONUS_REFERRAL" -> {
                sessionService.setUserState(chatId, "BONUS_REFERRAL");
                sendReferralMenu(chatId);
            }
            case "BONUS_TOPUP" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP");
                sendTopUpPlatformMenu(chatId);
            }
            case "BONUS_TOPUP_USER_ID", "BONUS_TOPUP_APPROVE_USER" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
                sendUserIdInput(chatId, sessionService.getUserData(chatId, "platform"));
            }
            case "BONUS_TOPUP_INPUT" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
                String platform = sessionService.getUserData(chatId, "platform");
                sendTopUpInput(chatId, platform);
            }
            case "BONUS_TOPUP_CONFIRM" -> {
                sessionService.setUserState(chatId, "BONUS_TOPUP_CONFIRM");
                String platform = sessionService.getUserData(chatId, "platform");
                BigDecimal amount = new BigDecimal(sessionService.getUserData(chatId, "amount"));
                sendTopUpConfirmation(chatId, platform, amount);
            }
            default -> sendMainMenu(chatId);
        }
    }

    private void sendBonusMenu(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
        Long availableLimit = dailyStatsService.getAvailableLimit(chatId);
        Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimit(chatId);
        
        // Null-safety checks and default values
        Long tickets = balance.getTickets() != null ? balance.getTickets() : 0L;
        Long balanceValue = balance.getBalance() != null ? balance.getBalance().longValue() : 0L;
        Long availableLimitSafe = availableLimit != null ? availableLimit : 0L;
        Long effectiveDailyLimitSafe = effectiveDailyLimit != null ? effectiveDailyLimit : 0L;
        
        // Detailed logging for debugging
        logger.info("Bonus menu calculation for chatId {}: tickets={}, balance={}, availableLimit={} (Foyadalanish mumkin), effectiveDailyLimit={} (Umumiy limit)", 
                chatId, tickets, balanceValue, availableLimitSafe, effectiveDailyLimitSafe);
        
        // Verify parameter order matches message format
        logger.debug("Bonus menu format parameters: 1.tickets={}, 2.balance={}, 3.availableLimit={}, 4.effectiveDailyLimit={}", 
                tickets, balanceValue, availableLimitSafe, effectiveDailyLimitSafe);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.bonus_menu"),
                tickets, 
                balanceValue, 
                availableLimitSafe,
                effectiveDailyLimitSafe));
        message.setReplyMarkup(createBonusMenuKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void sendLotteryMenu(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        Long minTickets = configurationService.getMinTickets();
        Long maxTickets = configurationService.getMaxTickets();
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.lottery_menu"),
                balance.getTickets(), minTickets, maxTickets));
        message.setReplyMarkup(createLotteryKeyboard(chatId, balance.getTickets()));
        messageSender.sendMessage(message, chatId);
    }

    private void sendReferralMenu(Long chatId) {
        BigDecimal balance = getReferralBalance(chatId);
        Long referralCount = referralRepository.countByReferrerChatId(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.referral_menu"),
                referralCount, balance.longValue()));
        message.setReplyMarkup(createReferralKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void sendBuyTicketsMenu(Long chatId) {
        List<LotteryTicketBundle> bundles = bundleService.getActiveBundles();
        if (bundles.isEmpty()) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.no_bundles_available"));
            sendLotteryMenu(chatId);
            return;
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        StringBuilder menuText = new StringBuilder();
        menuText.append(languageSessionService.getTranslation(chatId, "message.buy_tickets_menu"));
        menuText.append("\n\n");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (LotteryTicketBundle bundle : bundles) {
            menuText.append(String.format("%d bilet - %,d so'm\n", 
                    bundle.getTicketQuantity(), bundle.getPrice().longValue()));
            rows.add(List.of(createButton(
                    String.format("%d bilet - %,d so'm", bundle.getTicketQuantity(), bundle.getPrice().longValue()),
                    "BONUS_LOTTERY_BUY_BUNDLE:" + bundle.getId())));
        }

        message.setText(menuText.toString());
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        messageSender.sendMessage(message, chatId);
    }

    private void sendBuyTicketsConfirmation(Long chatId, Long bundleId) {
        try {
            LotteryTicketBundle bundle = bundleService.findById(bundleId);
            if (!bundle.getIsActive()) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "message.bundle_not_available"));
                sendBuyTicketsMenu(chatId);
                return;
            }

            UserBalance balance = userBalanceRepository.findById(chatId)
                    .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());

            if (balance.getBalance().compareTo(bundle.getPrice()) < 0) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "message.insufficient_balance_tickets"));
                sendBuyTicketsMenu(chatId);
                return;
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(String.format(languageSessionService.getTranslation(chatId, "message.buy_tickets_confirmation"),
                    bundle.getTicketQuantity(), bundle.getPrice().longValue(), balance.getBalance().longValue()));

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    createButton(languageSessionService.getTranslation(chatId, "button.yes"),
                            "BONUS_LOTTERY_CONFIRM_BUY:" + bundleId),
                    createButton(languageSessionService.getTranslation(chatId, "button.no"),
                            "BONUS_LOTTERY_BUY")));
            rows.add(createNavigationButtons(chatId));
            markup.setKeyboard(rows);
            message.setReplyMarkup(markup);
            messageSender.sendMessage(message, chatId);
        } catch (IllegalStateException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.bundle_not_available"));
            sendBuyTicketsMenu(chatId);
        }
    }

    private void processTicketPurchase(Long chatId, Long bundleId) {
        try {
            LotteryTicketBundle bundle = bundleService.findById(bundleId);
            if (!bundle.getIsActive()) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "message.bundle_not_available"));
                sendBuyTicketsMenu(chatId);
                return;
            }

            // Safe pattern: get existing balance or create new one if truly doesn't exist
            Optional<UserBalance> balanceOpt = userBalanceRepository.findById(chatId);
            UserBalance balance;
            if (balanceOpt.isPresent()) {
                balance = balanceOpt.get();
            } else {
                // Double-check to prevent race condition overwrites
                if (userBalanceRepository.existsById(chatId)) {
                    balance = userBalanceRepository.findById(chatId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "UserBalance exists but not accessible for chatId: " + chatId));
                } else {
                    // Truly doesn't exist - safe to create
                    balance = UserBalance.builder()
                            .chatId(chatId)
                            .tickets(0L)
                            .balance(BigDecimal.ZERO)
                            .build();
                    balance = userBalanceRepository.save(balance);
                    logger.info("Created new UserBalance for chatId {} in processTicketPurchase", chatId);
                }
            }

            // Check balance
            if (balance.getBalance().compareTo(bundle.getPrice()) < 0) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "message.insufficient_balance_tickets"));
                sendBuyTicketsMenu(chatId);
                return;
            }

            // Check cooldown
            Long cooldownSeconds = lotteryConfigService.getPurchaseCooldownSeconds();
            if (!purchaseService.canPurchase(chatId, cooldownSeconds)) {
                long remainingSeconds = purchaseService.getRemainingCooldownSeconds(chatId, cooldownSeconds);
                long minutes = remainingSeconds / 60;
                long seconds = remainingSeconds % 60;
                String message = String.format(
                        languageSessionService.getTranslation(chatId, "message.ticket_purchase_cooldown"),
                        minutes, seconds);
                messageSender.sendMessage(chatId, message);
                sendBuyTicketsMenu(chatId);
                return;
            }

            // Process purchase
            balance.setBalance(balance.getBalance().subtract(bundle.getPrice()));
            balance.setTickets(balance.getTickets() + bundle.getTicketQuantity());
            userBalanceRepository.save(balance);

            // Update purchase time
            purchaseService.updatePurchaseTime(chatId);

            // Send admin log for ticket purchase
            String phoneNumber = blockedUserRepository.findByChatId(chatId)
                    .map(blockedUser -> blockedUser.getPhoneNumber())
                    .orElse("N/A");
            LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("GMT+5"));
            String logMessage = String.format(
                    "🎫 Chipta sotib olingan 🎫\n\n" +
                    "👤 User ID: `%d`\n" +
                    "📱 Telefon: %s\n" +
                    "🎟 Sotib olingan chiptalar: %d ta\n" +
                    "💰 Sarflangan summa: %,d so'm\n" +
                    "💸 Qolgan balans: %,d so'm\n" +
                    "🎫 Jami chiptalar: %d ta\n" +
                    "📅 [%s]",
                    chatId, phoneNumber, bundle.getTicketQuantity(), bundle.getPrice().longValue(),
                    balance.getBalance().longValue(), balance.getTickets(),
                    timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(logMessage);

            // Send success message
            messageSender.sendMessage(chatId,
                    String.format(languageSessionService.getTranslation(chatId, "message.tickets_purchased_success"),
                            bundle.getTicketQuantity(), bundle.getPrice().longValue(), balance.getTickets()));

            sendLotteryMenu(chatId);
        } catch (IllegalStateException e) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.bundle_not_available"));
            sendBuyTicketsMenu(chatId);
        } catch (Exception e) {
            logger.error("Error processing ticket purchase for chatId {}: {}", chatId, e.getMessage(), e);
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.callback_error"));
            sendLotteryMenu(chatId);
        }
    }

    private void sendReferralLink(Long chatId) {
        String referralLink = String.format("https://t.me/Baronpeybot?start=ref_%d", chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.enableMarkdown(true);
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.referral_link"),
                referralLink));
        
        // Create keyboard with clickable URL button
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        // Add clickable referral link button
        rows.add(List.of(createButton(
                languageSessionService.getTranslation(chatId, "button.referral_link"),
                referralLink)));
        // Add navigation buttons
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        
        message.setReplyMarkup(markup);
        messageSender.sendMessage(message, chatId);
    }

    private void sendTopUpPlatformMenu(Long chatId) {
        BigDecimal balance = getReferralBalance(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        BigDecimal minTopUp = configurationService.getBonusTopUpMinAmount();
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.topup_menu"),
                balance.longValue(), minTopUp.longValue()));
        message.setReplyMarkup(createTopUpPlatformKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void sendUserIdInput(Long chatId, String platform) {
        List<HizmatRequest> recentRequests = requestRepository.findTop3ByChatIdAndPlatformOrderByCreatedAtDesc(chatId,
                platform);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        if (!recentRequests.isEmpty()) {
            HizmatRequest latestRequest = recentRequests.get(0);
            sessionService.setUserData(chatId, "platformUserId", latestRequest.getPlatformUserId());
            message.setText(languageSessionService.getTranslation(chatId, "message.user_id_with_recent"));
            message.setReplyMarkup(createSavedIdKeyboard(chatId, recentRequests));
        } else {
            message.setText(
                    String.format(languageSessionService.getTranslation(chatId, "message.user_id_input"), platform));
            message.setReplyMarkup(createNavigationKeyboard(chatId));
        }
        messageSender.sendMessage(message, chatId);
    }

    private void sendUserApproval(Long chatId, String fullName, String userId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.user_approval"),
                fullName, userId));
        message.setReplyMarkup(createApprovalKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void sendNoUserFound(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(languageSessionService.getTranslation(chatId, "message.no_user_found"));
        message.setReplyMarkup(createNavigationKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void sendTopUpInput(Long chatId, String platform) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        BigDecimal minTopUp = configurationService.getBonusTopUpMinAmount();
        BigDecimal maxTopUp = configurationService.getBonusTopUpMaxAmount();
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.topup_input"),
                platform, minTopUp.longValue(), maxTopUp.longValue()));
        message.setReplyMarkup(createAmountKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void sendTopUpConfirmation(Long chatId, String platform, BigDecimal amount) {
        String userId = sessionService.getUserData(chatId, "platformUserId");
        String fullName = sessionService.getUserData(chatId, "fullName");
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(String.format(languageSessionService.getTranslation(chatId, "message.topup_confirmation"),
                userId, fullName, platform, userId, amount.longValue()));
        message.setReplyMarkup(createConfirmKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private void handleUserIdInput(Long chatId, String userId) {
        // messageSender.animateAndDeleteMessages(chatId,
        // sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        if (!isValidUserId(userId)) {
            logger.warn("Invalid user ID format for chatId {}: {}", chatId, userId);
            messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.invalid_user_id"));
            String platform = sessionService.getUserData(chatId, "platform");
            sendUserIdInput(chatId, platform);
            return;
        }
        validateUserId(chatId, userId);
    }

    private void validateUserId(Long chatId, String userId) {
        String platformName = sessionService.getUserData(chatId, "platform");

        // --- Granular Permission Check ---
        String trimmedUserId = userId != null ? userId.trim() : "";
        Optional<UserPlatformPermission> permission = permissionRepository.findByUserId(trimmedUserId);
        if (permission.isPresent() && !permission.get().isCanBonusTopUp()) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.permission_denied_bonus"));
            sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
            sendUserIdInput(chatId, platformName);
            return;
        }

        // --- NEW PROMO LOGIC START ---
        if (featureService.isPromoEnabled()) {
            boolean allowed = promoWhitelistService.isPromoChatAllowed(chatId)
                    && promoWhitelistService.isPromoLinkAllowed(chatId, trimmedUserId);

            if (!allowed) {
                messageSender.sendMessage(chatId,
                        languageSessionService.getTranslation(chatId, "message.promo_restriction"));
                sessionService.setUserState(chatId, "BONUS_TOPUP_USER_ID");
                sendUserIdInput(chatId, platformName);
                return;
            }
        }
        // --- NEW PROMO LOGIC END ---
        Platform platform = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        if (platform.getType().equals("mostbet")) {
            Currency currency = platform.getCurrency();
            HizmatRequest request = HizmatRequest.builder()
                    .chatId(chatId)
                    .platform(platformName)
                    .platformUserId(userId)
                    .fullName("MOSTBET")
                    .status(RequestStatus.PENDING)
                    .createdAt(LocalDateTime.now(ZoneId.of("GMT+5")))
                    .amount(0L)
                    .currency(currency)
                    .type(RequestType.TOP_UP)
                    .build();
            requestRepository.save(request);

            sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
            sessionService.addNavigationState(chatId, "BONUS_TOPUP_APPROVE_USER");
            sessionService.setUserData(chatId, "platformUserId", userId);
            sessionService.setUserData(chatId, "fullName", "MOSTBET");
            sendTopUpInput(chatId, platformName);
        } else {
            String hash = platform.getApiKey();
            String cashierPass = platform.getPassword();
            String cashdeskId = platform.getWorkplaceId();

            String confirmInput = userId + ":" + hash;
            String confirm = DigestUtils.md5DigestAsHex(confirmInput.getBytes(StandardCharsets.UTF_8));

            String sha256Input1 = "hash=" + hash + "&userid=" + userId + "&cashdeskid=" + cashdeskId;
            String sha256Result1 = sha256Hex(sha256Input1);
            String md5Input = "userid=" + userId + "&cashierpass=" + cashierPass + "&hash=" + hash;
            String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
            String finalSignature = sha256Hex(sha256Result1 + md5Result);

            String apiUrl = String.format(
                    "https://partners.servcul.com/CashdeskBotAPI/Users/%s?confirm=%s&cashdeskId=%s",
                    userId, confirm, cashdeskId);
            logger.info("Validating user ID {} for platform {} (chatId: {}), URL: {}", userId, platformName, chatId,
                    apiUrl);

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("sign", finalSignature);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<UserProfile> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity,
                        UserProfile.class);
                UserProfile profile = response.getBody();

                if (response.getStatusCode().is2xxSuccessful() && profile != null && profile.getUserId() != null
                        && !profile.getName().isEmpty()) {
                    String fullName = profile.getName();
                    sessionService.setUserData(chatId, "platformUserId", userId);
                    sessionService.setUserData(chatId, "fullName", fullName);
                    Currency currency = Currency.UZS;
                    if (profile.getCurrencyId() == 1L) {
                        currency = Currency.RUB;
                    }
                    HizmatRequest request = HizmatRequest.builder()
                            .chatId(chatId)
                            .platform(platformName)
                            .platformUserId(userId)
                            .fullName(fullName)
                            .status(RequestStatus.PENDING)
                            .createdAt(LocalDateTime.now(ZoneId.of("GMT+5")))
                            .amount(0L)
                            .currency(currency)
                            .type(RequestType.TOP_UP)
                            .build();
                    requestRepository.save(request);

                    sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
                    sessionService.addNavigationState(chatId, "BONUS_TOPUP_APPROVE_USER");
                    sendTopUpInput(chatId, platformName);
                } else {
                    logger.warn("Invalid user profile for ID {} on platform {}. Response: {}", userId, platformName,
                            profile);
                    sendNoUserFound(chatId);
                }
            } catch (HttpClientErrorException.NotFound e) {
                logger.warn("User not found for ID {} on platform {}: {}", userId, platformName, e.getMessage());
                sendNoUserFound(chatId);
            } catch (HttpClientErrorException e) {
                logger.error("API error for user ID {} on platform {}: {}", userId, platformName, e.getMessage());
                String errorMessage = e.getStatusCode().value() == 401
                        ? languageSessionService.getTranslation(chatId, "message.api_error_invalid_signature")
                        : e.getStatusCode().value() == 403
                                ? languageSessionService.getTranslation(chatId, "message.api_error_invalid_confirm")
                                : languageSessionService.getTranslation(chatId, "message.api_error");
                messageSender.sendMessage(chatId, errorMessage);
                sendUserIdInput(chatId, platformName);
            } catch (Exception e) {
                logger.error("Error calling API for user ID {} on platform {}: {}", userId, platformName,
                        e.getMessage());
                messageSender.sendMessage(chatId, languageSessionService.getTranslation(chatId, "message.api_error"));
                sendUserIdInput(chatId, platformName);
            }
        }
    }

    private void handleApproveUser(Long chatId) {
        sessionService.setUserState(chatId, "BONUS_TOPUP_INPUT");
        sessionService.addNavigationState(chatId, "BONUS_TOPUP_APPROVE_USER");
        String platform = sessionService.getUserData(chatId, "platform");
        if (platform == null) {
            logger.error("Platform is null for chatId {}", chatId);
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.request_not_found"));
            sendUserIdInput(chatId, null);
        } else {
            sendTopUpInput(chatId, platform);
        }
    }

    private void handleTopUpInput(Long chatId, String input) {
        // messageSender.animateAndDeleteMessages(chatId,
        // sessionService.getMessageIds(chatId), "OPEN");
        sessionService.clearMessageIds(chatId);

        String amountStr = input.trim();
        String platform = sessionService.getUserData(chatId, "platform");

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);

            BigDecimal minTopUp = configurationService.getBonusTopUpMinAmount();
            BigDecimal maxTopUp = configurationService.getBonusTopUpMaxAmount();
            if (amount.compareTo(minTopUp) < 0 || amount.compareTo(maxTopUp) > 0) {
                String message = String.format(
                        languageSessionService.getTranslation(chatId, "message.invalid_amount_range"),
                        minTopUp.longValue(), maxTopUp.longValue());
                messageSender.sendMessage(chatId, message);
                sendTopUpInput(chatId, platform);
                return;
            }

            UserBalance balance = userBalanceRepository.findById(chatId)
                    .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());

            if (balance.getBalance().compareTo(minTopUp) < 0) {
                String message = String.format(
                        languageSessionService.getTranslation(chatId, "message.insufficient_minimum_balance"),
                        minTopUp.longValue(), balance.getBalance().longValue());
                messageSender.sendMessage(chatId, message);
                sendTopUpInput(chatId, platform);
                return;
            }

            if (balance.getBalance().compareTo(amount) < 0) {
                messageSender.sendMessage(chatId,
                        String.format(languageSessionService.getTranslation(chatId, "message.insufficient_balance"),
                                balance.getBalance().longValue()));
                sendTopUpInput(chatId, platform);
                return;
            }

            // Check daily bonus transfer limit
            if (featureService.isBonusLimitEnabled()) {
                Long availableLimit = dailyStatsService.getAvailableLimit(chatId);
                if (amount.longValue() > availableLimit) {
                    String errorMessage = String.format(
                            languageSessionService.getTranslation(chatId, "message.daily_limit_exceeded"),
                            availableLimit);
                    messageSender.sendMessage(chatId, errorMessage);
                    sendTopUpInput(chatId, platform);
                    return;
                }
            }

        } catch (NumberFormatException e) {
            logger.warn("Invalid amount format for chatId {}: {}", chatId, amountStr);
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.invalid_amount_format"));
            sendTopUpInput(chatId, platform);
            return;
        }

        sessionService.setUserData(chatId, "amount", amount.toString());
        sessionService.setUserState(chatId, "BONUS_TOPUP_CONFIRM");
        sessionService.addNavigationState(chatId, "BONUS_TOPUP_INPUT");
        sendTopUpConfirmation(chatId, platform, amount);
    }

    /**
     * Submits bonus top-up for admin approval. Must run through Spring proxy ({@link #bonusServiceProxy}) so
     * {@link Transactional} applies and pessimistic locks serialize duplicate inline-button taps.
     */
    @Transactional
    public void initiateTopUpRequest(Long chatId) {
        String platform = sessionService.getUserData(chatId, "platform");
        String userId = sessionService.getUserData(chatId, "platformUserId");
        String amountStr = sessionService.getUserData(chatId, "amount");
        String fullName = sessionService.getUserData(chatId, "fullName");

        BigDecimal amount = new BigDecimal(amountStr);
        Optional<UserBalance> balanceOpt = userBalanceRepository.findByIdWithLock(chatId);
        UserBalance balance;
        if (balanceOpt.isPresent()) {
            balance = balanceOpt.get();
        } else {
            if (userBalanceRepository.existsById(chatId)) {
                balance = userBalanceRepository.findByIdWithLock(chatId)
                    .orElseThrow(() -> new IllegalStateException("UserBalance exists but not accessible for chatId: " + chatId));
            } else {
                balance = UserBalance.builder()
                    .chatId(chatId)
                    .tickets(0L)
                    .balance(BigDecimal.ZERO)
                        .build();
                balance = userBalanceRepository.save(balance);
                logger.info("Created new UserBalance for chatId {}", chatId);
            }
        }

        if (balance.getBalance().compareTo(amount) < 0) {
            logger.warn("Insufficient balance for chatId {}: requested {}, available {}", chatId, amount,
                    balance.getBalance());
            messageSender.sendMessage(chatId,
                    String.format(languageSessionService.getTranslation(chatId, "message.topup_insufficient_balance"),
                            balance.getBalance().longValue()));
            sendTopUpInput(chatId, platform);
            return;
        }
        HizmatRequest request = requestRepository
                .findTopByChatIdAndPlatformAndPlatformUserIdAndStatusForUpdate(
                        chatId, platform, userId, RequestStatus.PENDING)
                .orElse(null);
        if (request == null) {
            logger.warn("No PENDING bonus request for chatId {}, platform: {}, userId: {} (duplicate confirm or wrong state)",
                    chatId, platform, userId);
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.request_not_found"));
            sendMainMenu(chatId);
            return;
        }
        balance.setBalance(balance.getBalance().subtract(new BigDecimal(amount.longValue())));
        userBalanceRepository.save(balance);
        dailyStatsService.subtractTopUpAmount(chatId, amount.longValue());
        dailyStatsService.addTransferAmount(chatId, amount.longValue()); // Decrease limit immediately; never restored on reject/fail

        request.setAmount(amount.longValue());
        request.setUniqueAmount(amount.longValue());
        request.setStatus(RequestStatus.PENDING_ADMIN);
        requestRepository.save(request);
        String now = LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String userMessage = String.format(languageSessionService.getTranslation(chatId, "message.topup_request_sent"),
                request.getId(), request.getPlatform(), request.getPlatformUserId(), request.getAmount(), now);
        messageSender.sendMessage(chatId, userMessage);

        if (featureService.isBonusAutoApproveEnabled()) {
            Long requestId = request.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bonusServiceProxy.handleAdminApproveTransfer(chatId, requestId);
                }
            });
        } else {
            sendAdminApprovalRequest(chatId, request);
        }

        clearTransferSessionData(chatId);

        sessionService.setUserState(chatId, "BONUS_MENU");
        sendBonusMenu(chatId);
    }

    /**
     * Clears transfer-specific session data (platformUserId, amount, fullName)
     * while keeping platform in session for potential reuse.
     */
    private void clearTransferSessionData(Long chatId) {
        sessionService.removeUserData(chatId, "platformUserId");
        sessionService.removeUserData(chatId, "amount");
        sessionService.removeUserData(chatId, "fullName");
        logger.debug("Cleared transfer session data for chatId: {}", chatId);
    }

    private void sendAdminApprovalRequest(Long chatId, HizmatRequest request) {
        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();
        String message = String.format(
                "*#Bonus pul yechish so'rovi:*\n\n" +
                        "\uD83C\uDD94: `%d`\n" +
                        "🌐 *%s:* `%s`\n" +
                        "💰 *Summa:* `%,d so‘m`\n" +
                        "👤 *Foydalanuvchi:* `%d`\n" +
                        "📞 *Telefon:* `%s`\n\n" +
                        "*Tasdiqlaysizmi?*\n\n" +
                        "📅 [%s]",
                request.getId(),
                request.getPlatform(),
                escapeMarkdown(request.getPlatformUserId()),
                request.getAmount(),
                chatId,
                escapeMarkdown(number),
                LocalDateTime.now(ZoneId.of("GMT+5"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        adminLogBotService.sendWithdrawRequestToAdmins(chatId, message, request.getId(),
                createAdminApprovalKeyboard(chatId, request.getId(), request.getChatId()));
    }

    private String escapeMarkdown(String text) {
        if (text == null)
            return "";
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[");
    }

    /**
     * Persists BONUS_APPROVED in its own transaction so dashboard counting is not lost
     * when later notify/messaging code fails and rolls back the outer transfer transaction.
     * Must be invoked through {@link #bonusServiceProxy}.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public HizmatRequest markBonusApproved(Long requestId) {
        HizmatRequest request = requestRepository.findByIdWithLock(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));
        if (request.getStatus() == RequestStatus.BONUS_APPROVED) {
            return request;
        }
        request.setStatus(RequestStatus.BONUS_APPROVED);
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            request.setTransactionId(UUID.randomUUID().toString());
        }
        request.setApprovedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        if (request.getUniqueAmount() == null || request.getUniqueAmount() <= 0) {
            if (request.getAmount() != null && request.getAmount() > 0) {
                request.setUniqueAmount(request.getAmount());
            }
        }
        return requestRepository.saveAndFlush(request);
    }

    @Transactional
    public void handleAdminApproveTransfer(Long chatId, Long requestId) {
        HizmatRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));

        if (request.getStatus() == RequestStatus.BONUS_APPROVED) {
            logger.debug("Bonus request {} already approved, skipping duplicate approval", requestId);
            return;
        }
        if (request.getStatus() != RequestStatus.PENDING_ADMIN) {
            logger.warn("Cannot approve bonus request {} in status {}", requestId, request.getStatus());
            return;
        }

        // creditReferral(request.getChatId(), request.getAmount());

        String platformName = request.getPlatform();
        Platform platformData = platformRepository.findByName(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        if (platformData.getType().equals("mostbet")) {
            try {
                BalanceLimit transferSuccessful = mostbetService.transferToPlatform(request);
                // Detach stale PENDING_ADMIN instance so outer tx cannot overwrite BONUS_APPROVED.
                entityManager.detach(request);
                request = bonusServiceProxy.markBonusApproved(requestId);
                // messageSender.animateAndDeleteMessages(request.getChatId(),
                // sessionService.getMessageIds(request.getChatId()), "OPEN");
                sessionService.clearMessageIds(request.getChatId());
                String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();

                if (transferSuccessful == null) {
                    Long totalLimit = dailyStatsService.getEffectiveDailyLimit(request.getChatId());
                    Long availableLimit = dailyStatsService.getAvailableLimit(request.getChatId());
                    LocalDate today = LocalDate.now(ZoneId.of("GMT+5"));
                    Optional<DailyUserStats> dailyStatsOpt = dailyUserStatsRepository.findByChatIdAndDate(request.getChatId(), today);
                    Long dailyTransferAmount = dailyStatsOpt.map(DailyUserStats::getDailyTransferAmount).orElse(0L);
                    Long dailyTopUpAmount = dailyStatsOpt.map(DailyUserStats::getDailyTopUpAmount).orElse(0L);
                    BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(request.getChatId());
                    Long permanentLimitIncrease = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                    
                    // Retrieve bank balance for mostbet
                    BalanceLimit cashdeskBalance = null;
                    try {
                        String apiKey = platformData.getApiKey();
                        String secret = platformData.getSecret();
                        String cashpointId = platformData.getWorkplaceId();
                        if (apiKey != null && secret != null && cashpointId != null) {
                            MostbetService.BalanceResponse balanceResponse = mostbetService.getBalance(apiKey, secret, cashpointId);
                            if (balanceResponse != null) {
                                cashdeskBalance = new BalanceLimit(new BigDecimal(balanceResponse.balance()), null);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to retrieve balance for mostbet platform: {}", e.getMessage());
                    }
                    
                    String message = String.format(
                            "🆔: `%d` Bonus To'lov yakunlandi ✅\n" +
                                    "👤: [%d] %s\n" +
                                    "🌐 #%s: %s\n" +
                                    "💸 Miqdor: %,d UZS\n" +
                                    "\n🏦: %,d %s\n" +
                                    "\n📊 Limit: %,d / %,d so'm\n" +
                                    "📅 [%s]",
                            request.getId(), request.getChatId(), number,
                            request.getPlatform(), request.getPlatformUserId(),
                            request.getAmount(),
                            cashdeskBalance != null && cashdeskBalance.getBalance() != null 
                                    ? cashdeskBalance.getBalance().longValue() : 0L,
                            request.getCurrency().toString(),
                            totalLimit, availableLimit,
                            LocalDateTime.now(ZoneId.of("GMT+5"))
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    String bonusMessage = String.format(
                            languageSessionService.getTranslation(request.getChatId(), "message.bonus_approved"),
                            request.getId(), request.getPlatform(), request.getPlatformUserId(), request.getAmount(),
                            LocalDateTime.now(ZoneId.of("GMT+5"))
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    messageSender.sendMessage(request.getChatId(), bonusMessage);
                    adminLogBotService.sendToAdmins(message);
                } else {
                    Long totalLimit = dailyStatsService.getEffectiveDailyLimit(request.getChatId());
                    Long availableLimit = dailyStatsService.getAvailableLimit(request.getChatId());
                    LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("GMT+5"));
                    LocalDate today = LocalDate.now(ZoneId.of("GMT+5"));
                    Optional<DailyUserStats> dailyStatsOpt = dailyUserStatsRepository.findByChatIdAndDate(request.getChatId(), today);
                    Long dailyTransferAmount = dailyStatsOpt.map(DailyUserStats::getDailyTransferAmount).orElse(0L);
                    Long dailyTopUpAmount = dailyStatsOpt.map(DailyUserStats::getDailyTopUpAmount).orElse(0L);
                    BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(request.getChatId());
                    Long permanentLimitIncrease = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                    
                    String message = String.format(
                            "🆔: `%d` Bonus To'lov yakunlandi ✅\n" +
                                    "👤: [%d] %s\n" +
                                    "🌐 #%s: %s\n" +
                                    "💸 Miqdor: %,d UZS\n" +
                                    "\n🏦: %,d %s\n" +
                                    "\n📊 Limit: %,d / %,d so'm\n" +
                                    "📅 [%s]",
                            request.getId(), request.getChatId(), number,
                            request.getPlatform(), request.getPlatformUserId(),
                            request.getAmount(),
                            transferSuccessful.getLimit().longValue(), platformData.getCurrency().toString(),
                            totalLimit, availableLimit,
                            timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    String bonusMessage = String.format(
                            languageSessionService.getTranslation(request.getChatId(), "message.bonus_approved"),
                            request.getId(), request.getPlatform(), request.getPlatformUserId(), request.getAmount(),
                            timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    messageSender.sendMessage(request.getChatId(), bonusMessage);
                    adminLogBotService.sendToAdmins(message);
                    // Send lotto bot notification for successful bonus top-up
                    lottoBotService.logBonusTopUpWin(request.getChatId(), request.getAmount(), request.getPlatform(), timestamp);
                }
            } catch (Exception e) {
                logger.error("❌ Error transferring top-up to platform for chatId {}: {}", request.getChatId(),
                        e.getMessage());
                messageSender.sendMessage(request.getChatId(),
                        languageSessionService.getTranslation(request.getChatId(), "message.transfer_failed"));
                adminLogBotService.sendToAdmins("So‘rov tasdiqlandi, lekin kontorada xatolik yuz berdi: "
                        + e.getMessage() + " (Foydalanuvchi: " + request.getChatId() + ")");
            }

        } else {

            String hash = platformData.getApiKey();
            String cashierPass = platformData.getPassword();
            String cashdeskId = platformData.getWorkplaceId();
            String lng = "uz";
            String userId = request.getPlatformUserId();
            String cardNumber = request.getCardNumber();
            ExchangeRate latest = exchangeRateRepository.findLatest()
                    .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
            long amount = request.getCurrency().equals(Currency.RUB) ? BigDecimal.valueOf(request.getAmount())
                    .multiply(latest.getUzsToRub())
                    .longValue() / 1000 : request.getAmount();
            if (hash == null || cashierPass == null || cashdeskId == null ||
                    hash.isEmpty() || cashierPass.isEmpty() || cashdeskId.isEmpty()) {
                logger.error("Invalid platform credentials for transfer {}", platformName);
                messageSender.sendMessage(request.getChatId(), languageSessionService
                        .getTranslation(request.getChatId(), "message.platform_credentials_error"));
                sendMainMenu(request.getChatId());
                return;
            }

            String confirm = DigestUtils.md5DigestAsHex((userId + ":" + hash).getBytes(StandardCharsets.UTF_8));
            String sha256Input = "hash=" + hash + "&lng=" + lng + "&userid=" + userId;
            String sha256Part = sha256Hex(sha256Input);
            String md5Input = "summa=" + amount + "&cashierpass=" + cashierPass + "&cashdeskid=" + cashdeskId;
            String md5Part = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
            String finalSignature = sha256Hex(sha256Part + md5Part);

            String apiUrl = String.format("https://partners.servcul.com/CashdeskBotAPI/Deposit/%s/Add", userId);
            HttpHeaders headers = new HttpHeaders();
            headers.set("sign", finalSignature);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("cashdeskId", Integer.parseInt(cashdeskId));
            body.put("lng", lng);
            body.put("summa", amount);
            body.put("confirm", confirm);
            body.put("cardNumber", cardNumber);

            try {
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
                Map<String, Object> responseBody = response.getBody();

                Object successObj = responseBody != null ? responseBody.get("success") : null;
                if (successObj == null && responseBody != null)
                    successObj = responseBody.get("Success");

                if (Boolean.TRUE.equals(successObj)) {
                    // Detach stale PENDING_ADMIN instance so outer tx cannot overwrite BONUS_APPROVED.
                    entityManager.detach(request);
                    request = bonusServiceProxy.markBonusApproved(requestId);
                    logger.info("✅ Platform transfer completed: chatId={}, userId={}, amount={}", request.getChatId(),
                            userId, amount);
                    // messageSender.animateAndDeleteMessages(request.getChatId(),
                    // sessionService.getMessageIds(request.getChatId()), "OPEN");
                    sessionService.clearMessageIds(request.getChatId());
                    String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();

                    BalanceLimit cashdeskBalance = getCashdeskBalance(hash, cashierPass, cashdeskId);
                    LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("GMT+5"));
                    if (cashdeskBalance == null) {
                        Long totalLimit = dailyStatsService.getEffectiveDailyLimit(request.getChatId());
                        Long availableLimit = dailyStatsService.getAvailableLimit(request.getChatId());
                        LocalDate today = LocalDate.now(ZoneId.of("GMT+5"));
                        Optional<DailyUserStats> dailyStatsOpt = dailyUserStatsRepository.findByChatIdAndDate(request.getChatId(), today);
                        Long dailyTransferAmount = dailyStatsOpt.map(DailyUserStats::getDailyTransferAmount).orElse(0L);
                        Long dailyTopUpAmount = dailyStatsOpt.map(DailyUserStats::getDailyTopUpAmount).orElse(0L);
                        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(request.getChatId());
                        Long permanentLimitIncrease = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                        
                        String message = String.format(
                                "🆔: `%d` Bonus To'lov yakunlandi ✅\n" +
                                        "👤: [%d] %s\n" +
                                        "🌐 #%s: %s\n" +
                                        "💸 Miqdor: %,d UZS\n" +
                                        "\n📊 Limit: %,d / %,d so'm\n" +
                                        "📅 [%s]",
                                request.getId(), request.getChatId(), number,
                                request.getPlatform(), request.getPlatformUserId(),
                                request.getAmount(),
                                totalLimit, availableLimit,
                                timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        String bonusMessage = String.format(
                                languageSessionService.getTranslation(request.getChatId(), "message.bonus_approved"),
                                request.getId(), request.getPlatform(), request.getPlatformUserId(),
                                request.getAmount(), timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        messageSender.sendMessage(request.getChatId(), bonusMessage);
                        adminLogBotService.sendToAdmins(message);
                        // Send lotto bot notification for successful bonus top-up
                        lottoBotService.logBonusTopUpWin(request.getChatId(), request.getAmount(), request.getPlatform(), timestamp);
                    } else {
                        Long totalLimit = dailyStatsService.getEffectiveDailyLimit(request.getChatId());
                        Long availableLimit = dailyStatsService.getAvailableLimit(request.getChatId());
                        LocalDate today = LocalDate.now(ZoneId.of("GMT+5"));
                        Optional<DailyUserStats> dailyStatsOpt = dailyUserStatsRepository.findByChatIdAndDate(request.getChatId(), today);
                        Long dailyTransferAmount = dailyStatsOpt.map(DailyUserStats::getDailyTransferAmount).orElse(0L);
                        Long dailyTopUpAmount = dailyStatsOpt.map(DailyUserStats::getDailyTopUpAmount).orElse(0L);
                        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(request.getChatId());
                        Long permanentLimitIncrease = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                        
                        String message = String.format(
                                "🆔: `%d` Bonus To'lov yakunlandi ✅\n" +
                                        "👤: [%d] %s\n" +
                                        "🌐 #%s: %s\n" +
                                        "💸 Miqdor: %,d UZS\n" +
                                        "\n🏦: %,d %s\n" +
                                        "\n📊 Limit: %,d / %,d so'm\n" +
                                        "📅 [%s]",
                                request.getId(), request.getChatId(), number,
                                request.getPlatform(), request.getPlatformUserId(),
                                request.getAmount(),
                                cashdeskBalance.getLimit().longValue(), platformData.getCurrency().toString(),
                                totalLimit, availableLimit,
                                timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        String bonusMessage = String.format(
                                languageSessionService.getTranslation(request.getChatId(), "message.bonus_approved"),
                                request.getId(), request.getPlatform(), request.getPlatformUserId(),
                                request.getAmount(), timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        messageSender.sendMessage(request.getChatId(), bonusMessage);
                        adminLogBotService.sendToAdmins(message);
                        // Send lotto bot notification for successful bonus top-up
                        lottoBotService.logBonusTopUpWin(request.getChatId(), request.getAmount(), request.getPlatform(), timestamp);
                    }
                } else {
                    String error = responseBody != null && responseBody.get("Message") != null
                            ? responseBody.get("Message").toString()
                            : "Platform javob bermadi.";
                    logger.error("❌ Transfer failed for chatId {}: {}", request.getChatId(), error);
                    adminLogBotService.sendToAdmins("So‘rov tasdiqlandi, lekin kontorada xatolik yuz berdi: " + error
                            + " (Foydalanuvchi: " + request.getChatId() + ")");
                    handleTransferFailure(chatId, request);
                }
            } catch (Exception e) {
                logger.error("❌ Error transferring top-up to platform for chatId {}: {}", request.getChatId(),
                        e.getMessage());
                messageSender.sendMessage(request.getChatId(),
                        languageSessionService.getTranslation(request.getChatId(), "message.transfer_failed"));
                adminLogBotService.sendToAdmins("So‘rov tasdiqlandi, lekin kontorada xatolik yuz berdi: "
                        + e.getMessage() + " (Foydalanuvchi: " + request.getChatId() + ")");
            }

            sendMainMenu(request.getChatId());
        }
    }

    private void handleTransferFailure(Long chatId, HizmatRequest request) {
        ExchangeRate latest = exchangeRateRepository.findLatest()
                .orElseThrow(() -> new RuntimeException("No exchange rate found in the database"));
        long amount = request.getCurrency().equals(Currency.RUB) ? BigDecimal.valueOf(request.getUniqueAmount())
                .multiply(latest.getUzsToRub())
                .longValue() / 1000 : request.getUniqueAmount();
        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();
        long rubAmount = BigDecimal.valueOf(request.getUniqueAmount())
                .multiply(latest.getUzsToRub())
                .longValue() / 1000;
        String cardDisplay = request.getCardNumber() != null ? request.getCardNumber() : "—";
        String errorLogMessage = String.format(
                "🆔: `%d` Transfer ❌\n" +
                        "👤: [%d] %s\n" +
                        "🌐 #%s %s🇺🇿:%s\n" +
                        "💸 Miqdor: %,d UZS\n" +
                        "💸 Miqdor: %,d RUB\n" +
                        "💳 Karta: %s\n" +
                        "📅 [%s]",
                request.getId(),
                request.getChatId(), number,
                request.getPlatform(), request.getCurrency().toString(), request.getPlatformUserId(),
                request.getUniqueAmount(), rubAmount,
                cardDisplay,
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        InlineKeyboardMarkup markup = createAdminBonusActionKeyboard(request.getChatId(), request.getId());

        adminLogBotService.sendToAdmins(errorLogMessage, markup);
        messageSender.sendMessage(request.getChatId(),
                languageSessionService.getTranslation(request.getChatId(), "message.transfer_failure"));
    }

    public void handleAdminDeclineTransfer(Long chatId, Long requestId) {
        HizmatRequest request = cancelBonusRequestIfPending(requestId);
        if (request == null) {
            return;
        }

        UserBalance balance = getOrCreateUserBalance(request.getChatId());

        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();
        String errorLogMessage = String.format(
                "🆔: `%d`\nBonus rad etildi (pul qaytarilmadi) ❌\n" +
                        "👤 User ID: `%s` %s\n" +
                        "🌐 %s: " + "`%s`\n" +
                        "💸 Bonus: %s \n" +
                        "💰 Balans: %s so‘m\n" +
                        "📅 [%s] ",
                request.getId(),
                request.getChatId(), number, request.getPlatform(), request.getPlatformUserId(),
                request.getUniqueAmount(), balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        String userErrorLogMessage = String.format(
                languageSessionService.getTranslation(request.getChatId(), "message.bonus_declined_no_refund"),
                request.getId(), request.getPlatform(), request.getPlatformUserId(), request.getUniqueAmount(),
                balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        SendMessage message = new SendMessage();
        message.setChatId(request.getChatId().toString());
        message.setText(userErrorLogMessage);
        message.setReplyMarkup(backButtonKeyboard(request.getChatId()));
        messageSender.sendMessage(message, request.getChatId());
        adminLogBotService.sendToAdmins(errorLogMessage);
    }

    public void handleAdminDeclineTransferWithRefund(Long chatId, Long requestId) {
        HizmatRequest request = cancelBonusRequestIfPending(requestId);
        if (request == null) {
            return;
        }

        UserBalance balance = getOrCreateUserBalance(request.getChatId());
        balance.setBalance(balance.getBalance().add(BigDecimal.valueOf(request.getAmount())));
        userBalanceRepository.save(balance);

        // Limit not restored on reject; it was consumed at request creation

        String number = blockedUserRepository.findByChatId(request.getChatId()).get().getPhoneNumber();
        String errorLogMessage = String.format(
                "🆔: `%d`\nBonus rad etildi ❌\n" +
                        "👤 User ID: `%s` %s\n" +
                        "🌐 %s: " + "`%s`\n" +
                        "💸 Bonus: %s \n" +
                        "💰 Balans: %s so‘m\n" +
                        "📅 [%s] ",
                request.getId(),
                request.getChatId(), number, request.getPlatform(), request.getPlatformUserId(),
                request.getUniqueAmount(), balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        String userErrorLogMessage = String.format(
                languageSessionService.getTranslation(request.getChatId(), "message.bonus_declined"),
                request.getId(), request.getPlatform(), request.getPlatformUserId(), request.getUniqueAmount(),
                balance.getBalance().longValue(),
                LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        SendMessage message = new SendMessage();
        message.setChatId(request.getChatId().toString());
        message.setText(userErrorLogMessage);
        message.setReplyMarkup(backButtonKeyboard(request.getChatId()));
        messageSender.sendMessage(message, request.getChatId());
        adminLogBotService.sendToAdmins(errorLogMessage);
    }

    private HizmatRequest cancelBonusRequestIfPending(Long requestId) {
        HizmatRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Request not found: " + requestId));
        if (request.getStatus() != RequestStatus.PENDING_ADMIN) {
            logger.warn("Bonus decline ignored for request {}: status is {}", requestId, request.getStatus());
            return null;
        }
        request.setStatus(RequestStatus.CANCELED);
        requestRepository.save(request);
        return request;
    }

    private UserBalance getOrCreateUserBalance(Long userChatId) {
        Optional<UserBalance> balanceOpt = userBalanceRepository.findById(userChatId);
        if (balanceOpt.isPresent()) {
            return balanceOpt.get();
        }
        if (userBalanceRepository.existsById(userChatId)) {
            return userBalanceRepository.findById(userChatId)
                    .orElseThrow(() -> new IllegalStateException(
                            "UserBalance exists but not accessible for chatId: " + userChatId));
        }
        UserBalance balance = UserBalance.builder()
                .chatId(userChatId)
                .tickets(0L)
                .balance(BigDecimal.ZERO)
                .build();
        balance = userBalanceRepository.save(balance);
        logger.info("Created new UserBalance for chatId {}", userChatId);
        return balance;
    }

    public void handleAdminRemoveTickets(Long chatId, Long userChatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null || !adminChat.isReceiveNotifications()) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.no_admin_permission"));
            return;
        }
        Optional<UserBalance> balanceOpt = userBalanceRepository.findById(userChatId);
        UserBalance balance;
        if (balanceOpt.isPresent()) {
            balance = balanceOpt.get();
        } else {
            // Double-check it doesn't exist (prevent race condition)
            if (userBalanceRepository.existsById(userChatId)) {
                // Entity exists but findById returned empty - fetch again
                balance = userBalanceRepository.findById(userChatId)
                    .orElseThrow(() -> new IllegalStateException("UserBalance exists but not accessible for chatId: " + userChatId));
            } else {
                // Truly doesn't exist - safe to create
                balance = UserBalance.builder()
                    .chatId(userChatId)
                    .tickets(0L)
                    .balance(BigDecimal.ZERO)
                    .build();
                balance = userBalanceRepository.save(balance);
                logger.info("Created new UserBalance for chatId {}", userChatId);
            }
        }
        balance.setTickets(0L);
        userBalanceRepository.save(balance);

        messageSender.sendMessage(userChatId,
                languageSessionService.getTranslation(userChatId, "message.tickets_removed"));
        adminLogBotService.sendToAdmins("Chiptalar o‘chirildi: Foydalanuvchi: " + userChatId);
    }

    public void handleAdminRemoveBonus(Long chatId, Long userChatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null || !adminChat.isReceiveNotifications()) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.no_admin_permission"));
            return;
        }
        Optional<UserBalance> balanceOpt = userBalanceRepository.findById(userChatId);
        UserBalance balance;
        if (balanceOpt.isPresent()) {
            balance = balanceOpt.get();
        } else {
            // Double-check it doesn't exist (prevent race condition)
            if (userBalanceRepository.existsById(userChatId)) {
                // Entity exists but findById returned empty - fetch again
                balance = userBalanceRepository.findById(userChatId)
                    .orElseThrow(() -> new IllegalStateException("UserBalance exists but not accessible for chatId: " + userChatId));
            } else {
                // Truly doesn't exist - safe to create
                balance = UserBalance.builder()
                    .chatId(userChatId)
                    .tickets(0L)
                    .balance(BigDecimal.ZERO)
                    .build();
                balance = userBalanceRepository.save(balance);
                logger.info("Created new UserBalance for chatId {}", userChatId);
            }
        }
        balance.setBalance(BigDecimal.ZERO);
        userBalanceRepository.save(balance);

        messageSender.sendMessage(userChatId,
                languageSessionService.getTranslation(userChatId, "message.bonus_removed"));
        adminLogBotService.sendToAdmins("Bonus balansi o‘chirildi: Foydalanuvchi: " + userChatId);
    }

    public void handleAdminBlockUser(Long chatId, Long userChatId) {
        AdminChat adminChat = adminChatRepository.findById(chatId).orElse(null);
        if (adminChat == null || !adminChat.isReceiveNotifications()) {
            messageSender.sendMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.no_admin_permission"));
            return;
        }
        BlockedUserService.BlockChatResult result = blockedUserService.blockChat(userChatId);
        if (result == BlockedUserService.BlockChatResult.ALREADY_BLOCKED) {
            messageSender.sendMessage(chatId, "❌ Bu foydalanuvchi allaqachon bloklangan.");
            return;
        }

        messageSender.sendMessage(userChatId,
                languageSessionService.getTranslation(userChatId, "message.user_blocked"));
        adminLogBotService.sendToAdmins("Foydalanuvchi bloklandi: Foydalanuvchi: " + userChatId);
    }

    @Async("bonusProcessingExecutor")
    @Transactional
    public void playLottery(Long chatId) {
        // Send immediate feedback to user
        messageSender.sendMessage(chatId,
                languageSessionService.getTranslation(chatId, "message.lottery_processing"));
        
        try {
            // Safe pattern: get existing balance or create new one if truly doesn't exist
            // Use pessimistic lock to prevent concurrent lottery plays
            Optional<UserBalance> balanceOpt = userBalanceRepository.findByIdWithLock(chatId);
            UserBalance balance;
            if (balanceOpt.isPresent()) {
                balance = balanceOpt.get();
            } else {
                // Double-check to prevent race condition overwrites
                if (userBalanceRepository.existsById(chatId)) {
                    balance = userBalanceRepository.findByIdWithLock(chatId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "UserBalance exists but not accessible for chatId: " + chatId));
                } else {
                    // Truly doesn't exist - safe to create
                    balance = UserBalance.builder()
                            .chatId(chatId)
                            .tickets(0L)
                            .balance(BigDecimal.ZERO)
                            .build();
                    balance = userBalanceRepository.save(balance);
                    logger.info("Created new UserBalance for chatId {} in playLottery", chatId);
                }
            }
            
            // Check cooldown
            Long cooldownSeconds = configurationService.getLotteryCooldownSeconds();
            LocalDateTime lastPlay = balance.getLastLotteryPlayTime();
            
            if (lastPlay != null) {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("GMT+5"));
                long secondsSinceLastPlay = ChronoUnit.SECONDS.between(lastPlay, now);
                
                if (secondsSinceLastPlay < cooldownSeconds) {
                    long remainingSeconds = cooldownSeconds - secondsSinceLastPlay;
                    long minutes = remainingSeconds / 60;
                    long seconds = remainingSeconds % 60;
                    
                    String message = String.format(
                            languageSessionService.getTranslation(chatId, "message.lottery_cooldown"),
                            minutes, seconds
                    );
                    messageSender.sendMessage(chatId, message);
                    sendLotteryMenu(chatId);
                    return;
                }
            }
            
            Long availableTickets = balance.getTickets();
            Long minTickets = configurationService.getMinTickets();
            Long maxTickets = configurationService.getMaxTickets();
            if (availableTickets < minTickets) {
                messageSender.sendMessage(chatId,
                        String.format(languageSessionService.getTranslation(chatId, "message.insufficient_tickets"),
                                minTickets, availableTickets));
                sendLotteryMenu(chatId);
                return;
            }

            Long numberOfPlays = Math.min(availableTickets, maxTickets);
            Map<Long, BigDecimal> ticketWinnings = lotteryService.playLotteryWithDetails(chatId, numberOfPlays);

            // NOTE: playLotteryWithDetails already deducts tickets and adds winnings to balance
            // We only need to calculate totalWinnings for logging/display and update lastLotteryPlayTime
            BigDecimal totalWinnings = ticketWinnings.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Re-fetch the updated balance to get correct values after playLotteryWithDetails modified it
            balance = userBalanceRepository.findById(chatId)
                    .orElseThrow(() -> new IllegalStateException("UserBalance not found after lottery play: " + chatId));
            balance.setLastLotteryPlayTime(LocalDateTime.now(ZoneId.of("GMT+5")));
            userBalanceRepository.save(balance);

            // Add lottery winnings percentage to daily limit increase
            long limitIncreaseJustAdded = 0L;
            BigDecimal winningsPercentage = lotteryConfigService.getWinningsPercentage();
            if (winningsPercentage.compareTo(BigDecimal.ZERO) > 0 && totalWinnings.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal limitIncrease = totalWinnings
                        .multiply(winningsPercentage)
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                if (limitIncrease.compareTo(BigDecimal.ZERO) > 0) {
                    limitIncreaseJustAdded = limitIncrease.longValue();
                    dailyStatsService.addLotteryWinningsLimitIncrease(chatId, limitIncreaseJustAdded);
                    logger.info("Added lottery winnings limit increase {} ({}% of {}) for chatId {}", 
                            limitIncreaseJustAdded, winningsPercentage, totalWinnings.longValue(), chatId);
                }
            }

            // Get total daily limit increase after this play
            Long totalDailyLimitIncrease = 0L;
            Long permanentIncreaseLong = 0L;
            Long baseLimit = dailyStatsService.getBaseDailyLimitForUser(chatId);
            Long tomorrowPermanentLimit = baseLimit; // Default to base limit if permanent increase retrieval fails
            try {
                Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimitReadOnly(chatId);
                BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
                permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                tomorrowPermanentLimit = baseLimit + permanentIncreaseLong;
                totalDailyLimitIncrease = effectiveDailyLimit - baseLimit - permanentIncreaseLong;
            } catch (Exception e) {
                logger.warn("Failed to get total daily limit increase for chatId {}: {}", chatId, e.getMessage());
                // Still try to get permanent limit for the log
                try {
                    BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
                    permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
                    tomorrowPermanentLimit = baseLimit + permanentIncreaseLong;
                } catch (Exception ex) {
                    logger.warn("Failed to get permanent limit increase for chatId {}: {}", chatId, ex.getMessage());
                }
            }

            StringBuilder winningsLog = new StringBuilder();
            ticketWinnings.forEach(
                    (ticketNumber, amount) -> winningsLog.append(String.format("%s UZS\n", formatWholeNumber(amount))));
            winningsLog.append(String.format(languageSessionService.getTranslation(chatId, "message.lottery_results"),
                    "", formatWholeNumber(totalWinnings), formatWholeNumber(balance.getBalance())));
            messageSender.sendMessage(chatId, winningsLog.toString());

            String number = blockedUserRepository.findByChatId(chatId).get().getPhoneNumber();
            LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("GMT+5"));
            String adminLog;
            if (limitIncreaseJustAdded > 0) {
                adminLog = String.format(
                        "Lotereya o'ynaldi 🎟\n" +
                                "👤 User ID: `%s` %s\n" +
                                "🎫 O'ynalgan chiptalar: %s ta\n" +
                                "💰 Jami yutuq: %s UZS\n" +
                                "📈 Limit oshdi (bu o'yin): %,d so'm\n" +
                                "📊 Limit: %,d / %,d so'm\n" +
                                "🎟️ Chiptalar: %,d ta\n" +
                                "💸 Yangi balans: %s UZS\n" +
                                "📅 [%s]",
                        chatId, number, numberOfPlays, formatWholeNumber(totalWinnings),
                        limitIncreaseJustAdded, totalDailyLimitIncrease, tomorrowPermanentLimit,
                        balance.getTickets(), balance.getBalance().toPlainString(),
                        timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } else {
                adminLog = String.format(
                        "Lotereya o'ynaldi 🎟\n" +
                                "👤 User ID: `%s` %s\n" +
                                "🎫 O'ynalgan chiptalar: %s ta\n" +
                                "💰 Jami yutuq: %s UZS\n" +
                                "📊 Limit: %,d / %,d so'm\n" +
                                "🎟️ Chiptalar: %,d ta\n" +
                                "💸 Yangi balans: %s UZS\n" +
                                "📅 [%s]",
                        chatId, number, numberOfPlays, formatWholeNumber(totalWinnings),
                        totalDailyLimitIncrease, tomorrowPermanentLimit,
                        balance.getTickets(), formatWholeNumber(balance.getBalance()),
                        timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            adminLogBotService.sendLog(adminLog);

            sendLotteryMenu(chatId);
        } catch (IllegalStateException e) {
            logger.error("Lottery play failed for chatId {}: {}", chatId, e.getMessage());
            messageSender.sendMessage(chatId, String
                    .format(languageSessionService.getTranslation(chatId, "message.lottery_error"), e.getMessage()));
            sendLotteryMenu(chatId);
        } catch (Exception e) {
            logger.error("Unexpected error in lottery play for chatId {}: {}", chatId, e.getMessage(), e);
            messageSender.sendMessage(chatId, String
                    .format(languageSessionService.getTranslation(chatId, "message.lottery_error"), e.getMessage()));
            sendLotteryMenu(chatId);
        }
    }

    public BigDecimal getReferralBalance(Long chatId) {
        UserBalance balance = userBalanceRepository.findById(chatId)
                .orElse(UserBalance.builder().chatId(chatId).tickets(0L).balance(BigDecimal.ZERO).build());
        return balance.getBalance();
    }

    public void creditReferral(Long referredChatId, long topUpAmount) {
        Referral referral = referralRepository.findByReferredChatId(referredChatId).orElse(null);
        if (referral == null) {
            logger.info("No referral found for referredChatId: {}", referredChatId);
            return;
        }

        Long referrerChatId = referral.getReferrerChatId();
        BigDecimal referralPercentage = configurationService.getReferralCommissionPercentage();
        BigDecimal commission = new BigDecimal(topUpAmount).multiply(referralPercentage).setScale(2, RoundingMode.DOWN);
        Optional<UserBalance> referrerBalanceOpt = userBalanceRepository.findById(referrerChatId);
        UserBalance referrerBalance;
        if (referrerBalanceOpt.isPresent()) {
            referrerBalance = referrerBalanceOpt.get();
        } else {
            // Double-check it doesn't exist (prevent race condition)
            if (userBalanceRepository.existsById(referrerChatId)) {
                // Entity exists but findById returned empty - fetch again
                referrerBalance = userBalanceRepository.findById(referrerChatId)
                    .orElseThrow(() -> new IllegalStateException("UserBalance exists but not accessible for referrerChatId: " + referrerChatId));
            } else {
                // Truly doesn't exist - safe to create
                referrerBalance = UserBalance.builder()
                    .chatId(referrerChatId)
                    .tickets(0L)
                    .balance(BigDecimal.ZERO)
                    .build();
                referrerBalance = userBalanceRepository.save(referrerBalance);
                logger.info("Created new UserBalance for referrer chatId {}", referrerChatId);
            }
        }
        referrerBalance.setBalance(referrerBalance.getBalance().add(commission));
        userBalanceRepository.save(referrerBalance);
        logger.info("Credited {} UZS to referrer {} for referredChatId {}", commission, referrerChatId, referredChatId);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return DatatypeConverter.printHexBinary(hash).toLowerCase();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    private void sendMainMenu(Long chatId) {
        sessionService.clearSession(chatId);
        sessionService.setUserState(chatId, "MAIN_MENU");
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(languageSessionService.getTranslation(chatId, "message.main_menu_welcome")); // From
                                                                                                     // ShadePaymentBot
        message.setReplyMarkup(createMainMenuKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createMainMenuKeyboard(Long chatId) {
        return com.example.shade.bot.MainMenuKeyboard.build(languageSessionService::getTranslation, chatId);
    }

    private InlineKeyboardMarkup createBonusMenuKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List
                .of(createButton(languageSessionService.getTranslation(chatId, "button.lottery"), "BONUS_LOTTERY")));
        rows.add(List
                .of(createButton(languageSessionService.getTranslation(chatId, "button.referral"), "BONUS_REFERRAL")));
        rows.add(List.of(createButton(
                languageSessionService.getTranslation(chatId, "wallet.button.tip"),
                "WALLET_TIP_MENU")));
        rows.add(List
                .of(createButton(languageSessionService.getTranslation(chatId, "button.topup_bonus"), "BONUS_TOPUP")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup backButtonKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createLotteryKeyboard(Long chatId, long ticketCount) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        Long minTickets = configurationService.getMinTickets();
        if (ticketCount >= minTickets) {
            rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.lottery_play"),
                    "BONUS_LOTTERY_PLAY")));
        }
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.buy_tickets"),
                "BONUS_LOTTERY_BUY")));
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.lottery_trade"),
                "LOTTERY_TRADE_MENU")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createReferralKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "button.referral_link"),
                "BONUS_REFERRAL_LINK")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createTopUpPlatformKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<Platform> uzsPlatforms = platformRepository.findByCurrency(Currency.UZS);
        List<Platform> rubPlatforms = platformRepository.findByCurrency(Currency.RUB);

        int maxRows = Math.max(uzsPlatforms.size(), rubPlatforms.size());
        for (int i = 0; i < maxRows; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            if (i < uzsPlatforms.size()) {
                Platform uzsPlatform = uzsPlatforms.get(i);
                row.add(createButton("🇺🇿 " + uzsPlatform.getName(), "BONUS_TOPUP_PLATFORM:" + uzsPlatform.getName()));
            }
            if (i < rubPlatforms.size()) {
                Platform rubPlatform = rubPlatforms.get(i);
                row.add(createButton("🇷🇺 " + rubPlatform.getName(), "BONUS_TOPUP_PLATFORM:" + rubPlatform.getName()));
            } else {
                i++;
                if (i < uzsPlatforms.size() && i < maxRows) {
                    Platform uzsPlatform = uzsPlatforms.get(i);
                    row.add(createButton("🇺🇿 " + uzsPlatform.getName(),
                            "BONUS_TOPUP_PLATFORM:" + uzsPlatform.getName()));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createSavedIdKeyboard(Long chatId, List<HizmatRequest> recentRequests) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (!recentRequests.isEmpty()) {
            List<InlineKeyboardButton> pastIdButtons = recentRequests.stream()
                    .map(HizmatRequest::getPlatformUserId)
                    .distinct()
                    .limit(2)
                    .map(id -> createButton("🆔 " + id, "BONUS_TOPUP_PAST_ID:" + id))
                    .collect(Collectors.toList());
            if (!pastIdButtons.isEmpty()) {
                rows.add(pastIdButtons);
            }
        }
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createApprovalKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "button.approve"),
                        "BONUS_TOPUP_APPROVE_USER"),
                createButton(languageSessionService.getTranslation(chatId, "button.reject"),
                        "BONUS_TOPUP_REJECT_USER")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createAmountKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        BigDecimal minAmount = configurationService.getBonusTopUpMinAmount();
        BigDecimal maxAmount = configurationService.getBonusTopUpMaxAmount();
        rows.add(List.of(
                createButton(String.format("%,d", minAmount.longValue()), "BONUS_TOPUP_AMOUNT_MIN"),
                createButton(String.format("%,d", maxAmount.longValue()), "BONUS_TOPUP_AMOUNT_MAX")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createConfirmKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(chatId, "button.yes"), "BONUS_TOPUP_CONFIRM_YES"),
                createButton(languageSessionService.getTranslation(chatId, "button.no"), "BONUS_TOPUP_CONFIRM_NO")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createAdminApprovalKeyboard(Long chatId, Long requestId, Long userChatId) {
        return createAdminBonusActionKeyboard(chatId, requestId);
    }

    private InlineKeyboardMarkup createAdminBonusActionKeyboard(Long labelChatId, Long requestId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(labelChatId, "button.approve_transfer"),
                        "ADMIN_APPROVE_TRANSFER:" + requestId),
                createButton(languageSessionService.getTranslation(labelChatId, "button.decline_transfer"),
                        "ADMIN_DECLINE_TRANSFER:" + requestId)));
        rows.add(List.of(
                createButton(languageSessionService.getTranslation(labelChatId, "button.decline_transfer_refund"),
                        "ADMIN_DECLINE_REFUND_TRANSFER:" + requestId)));
        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createNavigationKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> createNavigationButtons(Long chatId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "button.back"), "BACK"));
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "button.home"), "HOME"));
        return buttons;
    }

    private InlineKeyboardButton createButton(String text, String callbackOrUrl) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        if (callbackOrUrl.startsWith("http")) {
            button.setUrl(callbackOrUrl);
        } else {
            button.setCallbackData(callbackOrUrl);
        }
        return button;
    }

    private boolean isValidUserId(String userId) {
        return userId.matches("\\d+");
    }

    public BalanceLimit getCashdeskBalance(String hash, String cashierPass, String cashdeskId) {
        RestTemplate restTemplate = new RestTemplate();
        String baseUrl = "https://partners.servcul.com/CashdeskBotAPI";
        String dt = ZonedDateTime.now(ZoneId.of("GMT+5"))
                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));

        // Generate signature
        String sha256Input = String.format("hash=%s&cashierpass=%s&dt=%s", hash, cashierPass, dt);
        String sha256Result = sha256Hex(sha256Input);
        String md5Input = String.format("dt=%s&cashierpass=%s&cashdeskid=%s", dt, cashierPass, cashdeskId);
        String md5Result = DigestUtils.md5DigestAsHex(md5Input.getBytes(StandardCharsets.UTF_8));
        String finalSignature = sha256Hex(sha256Result + md5Result);

        // Generate confirm
        String confirm = DigestUtils.md5DigestAsHex((cashdeskId + ":" + hash).getBytes(StandardCharsets.UTF_8));

        // Build URL
        String url = String.format("%s/Cashdesk/%s/Balance?confirm=%s&dt=%s", baseUrl, cashdeskId, confirm, dt);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("sign", finalSignature);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Make GET request and extract balance
        Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
        Object balanceObj = response != null ? response.get("Balance") : null;
        Object limitObj = response != null ? response.get("Limit") : null;
        return balanceObj != null
                ? new BalanceLimit(new BigDecimal(balanceObj.toString()), new BigDecimal(limitObj.toString()))
                : null;
    }

    /**
     * Formats BigDecimal as whole number (no decimals) for lottery win messages.
     */
    private String formatWholeNumber(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
    }
}