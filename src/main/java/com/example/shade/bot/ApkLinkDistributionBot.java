package com.example.shade.bot;

import com.example.shade.model.ApkLinkPlatform;
import com.example.shade.service.ApkLinkBotConfigService;
import com.example.shade.service.ApkLinkCooldownService;
import com.example.shade.service.ApkLinkPlatformService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApkLinkDistributionBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(ApkLinkDistributionBot.class);
    private static final String PREFIX_PLATFORM = "PLATFORM:";
    private static final String PREFIX_SEND_LINK = "SEND:link:";
    private static final String PREFIX_SEND_APK = "SEND:apk:";

    private final ApkLinkBotConfigService configService;
    private final ApkLinkPlatformService platformService;
    private final ApkLinkCooldownService cooldownService;
    private final MessageSource messageSource;

    @Override
    public String getBotUsername() {
        return "ApkLinkDistributionBot";
    }

    @Override
    public String getBotToken() {
        return configService.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        String token = configService.getBotToken();
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
                return;
            }
            if (update.hasMessage() && update.getMessage().hasText()) {
                Long chatId = update.getMessage().getChatId();
                String chatType = update.getMessage().getChat().getType();
                if ("private".equals(chatType)) {
                    sendPlatformList(chatId);
                } else if ("group".equals(chatType) || "supergroup".equals(chatType)) {
                    handleGroupMessage(update.getMessage().getText(), chatId,
                            update.getMessage().getFrom().getId());
                }
            }
        } catch (Exception e) {
            logger.error("ApkLink bot error: {}", e.getMessage(), e);
        }
    }

    private void handleCallback(Update update) {
        org.telegram.telegrambots.meta.api.objects.CallbackQuery cq = update.getCallbackQuery();
        String data = cq.getData();
        Long chatId = cq.getMessage().getChatId();
        Long userId = cq.getFrom().getId();
        String callbackId = cq.getId();

        try {
            execute(new AnswerCallbackQuery(callbackId));
        } catch (TelegramApiException e) {
            logger.warn("Failed to answer callback: {}", e.getMessage());
        }

        if (data.startsWith(PREFIX_PLATFORM)) {
            String idStr = data.substring(PREFIX_PLATFORM.length()).trim();
            try {
                Long platformId = Long.parseLong(idStr);
                sendLinkOrApkChoice(chatId, platformId);
            } catch (NumberFormatException e) {
                sendText(chatId, "Invalid selection.");
            }
            return;
        }
        if (data.startsWith(PREFIX_SEND_LINK)) {
            String idStr = data.substring(PREFIX_SEND_LINK.length()).trim();
            try {
                Long platformId = Long.parseLong(idStr);
                handleSendLink(chatId, userId, platformId);
            } catch (NumberFormatException e) {
                sendText(chatId, "Invalid selection.");
            }
            return;
        }
        if (data.startsWith(PREFIX_SEND_APK)) {
            String idStr = data.substring(PREFIX_SEND_APK.length()).trim();
            try {
                Long platformId = Long.parseLong(idStr);
                handleSendApk(chatId, userId, platformId);
            } catch (NumberFormatException e) {
                sendText(chatId, "Invalid selection.");
            }
        }
    }

    private void sendPlatformList(Long chatId) {
        List<ApkLinkPlatform> platforms = platformService.findAllPlatforms();
        if (platforms.isEmpty()) {
            sendText(chatId, getMessage("apk_link.select_platform", Locale.ENGLISH) + " (no platforms configured)");
            return;
        }
        String text = getMessage("apk_link.select_platform", Locale.forLanguageTag("uz")) + "\n" +
                getMessage("apk_link.select_platform", Locale.forLanguageTag("ru"));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ApkLinkPlatform p : platforms) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(p.getName());
            btn.setCallbackData(PREFIX_PLATFORM + p.getId());
            rows.add(List.of(btn));
        }
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendLinkOrApkChoice(Long chatId, Long platformId) {
        Optional<ApkLinkPlatform> opt = platformService.findPlatformById(platformId);
        if (opt.isEmpty()) {
            sendText(chatId, "Platform not found.");
            return;
        }
        String text = getMessage("apk_link.choose_link_or_apk", Locale.forLanguageTag("uz")) + "\n" +
                getMessage("apk_link.choose_link_or_apk", Locale.forLanguageTag("ru"));
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton linkBtn = new InlineKeyboardButton();
        linkBtn.setText("Link");
        linkBtn.setCallbackData(PREFIX_SEND_LINK + platformId);
        InlineKeyboardButton apkBtn = new InlineKeyboardButton();
        apkBtn.setText("APK");
        apkBtn.setCallbackData(PREFIX_SEND_APK + platformId);
        row.add(linkBtn);
        row.add(apkBtn);
        sendMessageWithKeyboard(chatId, text, List.of(row));
    }

    private void handleSendLink(Long chatId, Long userId, Long platformId) {
        int cooldownMinutes = configService.getConfig()
                .map(c -> c.getCooldownPrivateMinutes() != null ? c.getCooldownPrivateMinutes() : 0)
                .orElse(0);
        Optional<Long> remaining = cooldownService.getRemainingMinutesUser(userId, cooldownMinutes);
        if (remaining.isPresent()) {
            sendCooldownMessage(chatId, remaining.get());
            return;
        }
        Optional<ApkLinkPlatform> platform = platformService.findPlatformById(platformId);
        if (platform.isEmpty()) {
            sendText(chatId, "Platform not found.");
            return;
        }
        cooldownService.applyUserCooldown(userId);
        sendText(chatId, platform.get().getLinkUrl());
    }

    private void handleSendApk(Long chatId, Long userId, Long platformId) {
        int cooldownMinutes = configService.getConfig()
                .map(c -> c.getCooldownPrivateMinutes() != null ? c.getCooldownPrivateMinutes() : 0)
                .orElse(0);
        Optional<Long> remaining = cooldownService.getRemainingMinutesUser(userId, cooldownMinutes);
        if (remaining.isPresent()) {
            sendCooldownMessage(chatId, remaining.get());
            return;
        }
        Optional<ApkLinkPlatform> platform = platformService.findPlatformById(platformId);
        if (platform.isEmpty()) {
            sendText(chatId, "Platform not found.");
            return;
        }
        ApkLinkPlatform p = platform.get();
        cooldownService.applyUserCooldown(userId);
        if (p.getApkFileId() != null && !p.getApkFileId().isEmpty()) {
            sendDocumentByFileId(chatId, p.getApkFileId(), p.getName());
        } else if (p.getApkUrl() != null && !p.getApkUrl().isEmpty()) {
            sendText(chatId, p.getApkUrl());
        } else {
            sendText(chatId, "APK not configured for this platform.");
        }
    }

    private void handleGroupMessage(String text, Long chatId, Long userId) {
        Optional<ApkLinkPlatform> platformOpt = platformService.findPlatformByKeyword(text);
        if (platformOpt.isEmpty()) {
            return;
        }
        ApkLinkPlatform platform = platformOpt.get();
        int cooldownMinutes = configService.getConfig()
                .map(c -> c.getCooldownGroupMinutes() != null ? c.getCooldownGroupMinutes() : 0)
                .orElse(0);
        boolean isAdmin = isChatAdmin(chatId, userId);
        if (!isAdmin) {
            Optional<Long> remaining = cooldownService.getRemainingMinutesGroup(chatId, cooldownMinutes);
            if (remaining.isPresent()) {
                sendCooldownMessage(chatId, remaining.get());
                return;
            }
            cooldownService.applyGroupCooldown(chatId);
        }
        sendText(chatId, platform.getLinkUrl());
        if (platform.getApkFileId() != null && !platform.getApkFileId().isEmpty()) {
            sendDocumentByFileId(chatId, platform.getApkFileId(), platform.getName());
        } else if (platform.getApkUrl() != null && !platform.getApkUrl().isEmpty()) {
            sendText(chatId, platform.getApkUrl());
        }
    }

    private boolean isChatAdmin(Long chatId, Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember(chatId.toString(), userId);
            ChatMember member = execute(getChatMember);
            String status = member.getStatus();
            return "creator".equals(status) || "administrator".equals(status);
        } catch (TelegramApiException e) {
            logger.warn("Could not get chat member for admin check: {}", e.getMessage());
            return false;
        }
    }

    private void sendCooldownMessage(Long chatId, long remainingMinutes) {
        String uz = getMessage("apk_link.cooldown_uz", new Object[]{remainingMinutes}, Locale.forLanguageTag("uz"));
        String ru = getMessage("apk_link.cooldown_ru", new Object[]{remainingMinutes}, Locale.forLanguageTag("ru"));
        sendText(chatId, uz + "\n" + ru);
    }

    private String getMessage(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }

    private String getMessage(String code, Object[] args, Locale locale) {
        return messageSource.getMessage(code, args, code, locale);
    }

    private void sendText(Long chatId, String text) {
        try {
            execute(new SendMessage(chatId.toString(), text));
        } catch (TelegramApiException e) {
            logger.error("Failed to send message: {}", e.getMessage());
        }
    }

    private void sendMessageWithKeyboard(Long chatId, String text, List<List<InlineKeyboardButton>> rows) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            msg.setReplyMarkup(new InlineKeyboardMarkup(rows));
            execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Failed to send message: {}", e.getMessage());
        }
    }

    private void sendDocumentByFileId(Long chatId, String fileId, String caption) {
        try {
            SendDocument doc = new SendDocument();
            doc.setChatId(chatId.toString());
            doc.setDocument(new InputFile(fileId));
            if (caption != null && !caption.isEmpty()) {
                doc.setCaption(caption);
            }
            execute(doc);
        } catch (TelegramApiException e) {
            logger.error("Failed to send document: {}", e.getMessage());
        }
    }
}
