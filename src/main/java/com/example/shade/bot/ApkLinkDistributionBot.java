package com.example.shade.bot;

import com.example.shade.model.ApkLinkBotConfig;
import com.example.shade.model.ApkLinkInvite;
import com.example.shade.model.ApkLinkPlatform;
import com.example.shade.service.ApkLinkBotConfigService;
import com.example.shade.service.ApkLinkCooldownService;
import com.example.shade.service.ApkLinkInviteService;
import com.example.shade.service.ApkLinkLanguageService;
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
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ApkLinkDistributionBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(ApkLinkDistributionBot.class);
    private static final String MAIN_LINK_APK = "MAIN_LINK_APK";
    private static final String MAIN_GROUP_CHANNEL = "MAIN_GROUP_CHANNEL";
    private static final String MAIN_CONTACTS = "MAIN_CONTACTS";
    private static final String BACK_MAIN = "BACK_MAIN";
    private static final String PREFIX_PLATFORM = "PLATFORM:";
    private static final String PREFIX_SEND_LINK = "SEND:link:";
    private static final String PREFIX_SEND_APK = "SEND:apk:";
    private static final String LANG_UZ = "LANG_UZ";
    private static final String LANG_RU = "LANG_RU";

    private final ApkLinkBotConfigService configService;
    private final ApkLinkLanguageService languageService;
    private final ApkLinkPlatformService platformService;
    private final ApkLinkInviteService inviteService;
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
                    if (languageService.getLanguageCode(chatId).isEmpty()) {
                        sendLanguageSelection(chatId);
                    } else {
                        sendMainMenu(chatId);
                    }
                } else if ("group".equals(chatType) || "supergroup".equals(chatType)) {
                    handleGroupMessage(update.getMessage().getText(), chatId,
                            update.getMessage().getFrom().getId());
                } else if ("channel".equals(chatType)) {
                    handleChannelMessage(update.getMessage().getText(), chatId);
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

        if (LANG_UZ.equals(data)) {
            languageService.setLanguage(chatId, "uz");
            sendMainMenu(chatId);
            return;
        }
        if (LANG_RU.equals(data)) {
            languageService.setLanguage(chatId, "ru");
            sendMainMenu(chatId);
            return;
        }
        if (BACK_MAIN.equals(data)) {
            sendMainMenu(chatId);
            return;
        }
        if (MAIN_LINK_APK.equals(data)) {
            sendPlatformList(chatId);
            return;
        }
        if (MAIN_GROUP_CHANNEL.equals(data)) {
            sendGroupChannelScreen(chatId);
            return;
        }
        if (MAIN_CONTACTS.equals(data)) {
            sendContactsScreen(chatId);
            return;
        }
        if (data.startsWith(PREFIX_PLATFORM)) {
            String idStr = data.substring(PREFIX_PLATFORM.length()).trim();
            try {
                Long platformId = Long.parseLong(idStr);
                sendLinkOrApkChoice(chatId, platformId);
            } catch (NumberFormatException e) {
                sendText(chatId, getMessage(chatId, "apk_link.invalid_selection"));
            }
            return;
        }
        if (data.startsWith(PREFIX_SEND_LINK)) {
            String idStr = data.substring(PREFIX_SEND_LINK.length()).trim();
            try {
                Long platformId = Long.parseLong(idStr);
                handleSendLink(chatId, userId, platformId);
            } catch (NumberFormatException e) {
                sendText(chatId, getMessage(chatId, "apk_link.invalid_selection"));
            }
            return;
        }
        if (data.startsWith(PREFIX_SEND_APK)) {
            String idStr = data.substring(PREFIX_SEND_APK.length()).trim();
            try {
                Long platformId = Long.parseLong(idStr);
                handleSendApk(chatId, userId, platformId);
            } catch (NumberFormatException e) {
                sendText(chatId, getMessage(chatId, "apk_link.invalid_selection"));
            }
        }
    }

    private void sendLanguageSelection(Long chatId) {
        String text = getMessage("apk_link.message.choose_language", Locale.forLanguageTag("uz")) + "\n" +
                getMessage("apk_link.message.choose_language", Locale.forLanguageTag("ru"));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createCallbackButton(getMessage("apk_link.button.language_uz", Locale.forLanguageTag("uz")), LANG_UZ),
                createCallbackButton(getMessage("apk_link.button.language_ru", Locale.forLanguageTag("ru")), LANG_RU)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendMainMenu(Long chatId) {
        String text = getMessage(chatId, "apk_link.main_menu");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.link_apk"), MAIN_LINK_APK)));
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.group_channel"), MAIN_GROUP_CHANNEL)));
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.contacts"), MAIN_CONTACTS)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendPlatformList(Long chatId) {
        List<ApkLinkPlatform> platforms = platformService.findAllPlatforms();
        if (platforms.isEmpty()) {
            sendText(chatId, getMessage(chatId, "apk_link.select_platform") + " " + getMessage(chatId, "apk_link.no_platforms_configured"));
            return;
        }
        String text = getMessage(chatId, "apk_link.select_platform");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < platforms.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            ApkLinkPlatform p0 = platforms.get(i);
            row.add(createCallbackButton(p0.getName(), PREFIX_PLATFORM + p0.getId()));
            if (i + 1 < platforms.size()) {
                ApkLinkPlatform p1 = platforms.get(i + 1);
                row.add(createCallbackButton(p1.getName(), PREFIX_PLATFORM + p1.getId()));
            }
            rows.add(row);
        }
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.back"), BACK_MAIN)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendLinkOrApkChoice(Long chatId, Long platformId) {
        Optional<ApkLinkPlatform> opt = platformService.findPlatformById(platformId);
        if (opt.isEmpty()) {
            sendText(chatId, getMessage(chatId, "apk_link.platform_not_found"));
            return;
        }
        String text = getMessage(chatId, "apk_link.choose_link_or_apk");
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton linkBtn = new InlineKeyboardButton();
        linkBtn.setText(getMessage(chatId, "apk_link.button.link"));
        linkBtn.setCallbackData(PREFIX_SEND_LINK + platformId);
        InlineKeyboardButton apkBtn = new InlineKeyboardButton();
        apkBtn.setText(getMessage(chatId, "apk_link.button.apk"));
        apkBtn.setCallbackData(PREFIX_SEND_APK + platformId);
        row.add(linkBtn);
        row.add(apkBtn);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row);
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.back"), BACK_MAIN)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void handleSendLink(Long chatId, Long userId, Long platformId) {
        int cooldownMinutes = configService.getConfig()
                .map(c -> c.getCooldownPrivateMinutes() != null ? c.getCooldownPrivateMinutes() : 0)
                .orElse(0);
        Optional<Long> remaining = cooldownService.getRemainingMinutesUser(userId, cooldownMinutes);
        if (remaining.isPresent()) {
            Optional<String> channelLink = configService.getApkChannelMessageLink();
            if (channelLink.isPresent()) {
                sendCooldownRedirectToChannel(chatId, channelLink.get());
            } else {
                sendCooldownMessageWithBackButton(chatId, remaining.get());
            }
            return;
        }
        Optional<ApkLinkPlatform> platform = platformService.findPlatformById(platformId);
        if (platform.isEmpty()) {
            sendText(chatId, getMessage(chatId, "apk_link.platform_not_found"));
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
            Optional<String> channelLink = configService.getApkChannelMessageLink();
            if (channelLink.isPresent()) {
                sendCooldownRedirectToChannel(chatId, channelLink.get());
            } else {
                sendCooldownMessageWithBackButton(chatId, remaining.get());
            }
            return;
        }
        Optional<ApkLinkPlatform> platform = platformService.findPlatformById(platformId);
        if (platform.isEmpty()) {
            sendText(chatId, getMessage(chatId, "apk_link.platform_not_found"));
            return;
        }
        cooldownService.applyUserCooldown(userId);
        Optional<String> channelLink = configService.getApkChannelMessageLink();
        if (channelLink.isPresent()) {
            sendText(chatId, channelLink.get());
            return;
        }
        ApkLinkPlatform p = platform.get();
        if (p.getApkFileId() != null && !p.getApkFileId().isEmpty()) {
            sendDocumentByFileId(chatId, p.getApkFileId(), p.getName());
        } else if (p.getApkUrl() != null && !p.getApkUrl().isEmpty()) {
            sendText(chatId, p.getApkUrl());
        } else {
            sendText(chatId, getMessage(chatId, "apk_link.apk_not_configured"));
        }
    }

    private void handleChannelMessage(String text, Long chatId) {
        if (text == null) return;
        String keyword = configService.getConfig()
                .map(ApkLinkBotConfig::getChannelKeywordAllApk)
                .orElse(null);
        if (keyword == null || keyword.isEmpty() || !text.trim().equals(keyword.trim())) {
            return;
        }
        List<ApkLinkPlatform> withApk = platformService.findAllPlatforms().stream()
                .filter(p -> (p.getApkFileId() != null && !p.getApkFileId().isEmpty())
                        || (p.getApkUrl() != null && !p.getApkUrl().isEmpty()))
                .collect(Collectors.toList());
        if (withApk.isEmpty()) {
            sendText(chatId, getMessage(chatId, "apk_link.no_apks_configured"));
            return;
        }
        try {
            if (withApk.size() == 1) {
                ApkLinkPlatform p = withApk.get(0);
                String caption = p.getApkFileName() != null && !p.getApkFileName().isEmpty() ? p.getApkFileName() : p.getName();
                SendDocument doc = new SendDocument();
                doc.setChatId(chatId.toString());
                if (p.getApkFileId() != null && !p.getApkFileId().isEmpty()) {
                    doc.setDocument(new InputFile(p.getApkFileId()));
                } else {
                    doc.setDocument(new InputFile(p.getApkUrl()));
                }
                doc.setCaption(caption);
                Message sent = execute(doc);
                configService.saveApkChannelMessageLink(sent.getChatId(), sent.getMessageId());
            } else {
                final int batchSize = 10;
                for (int i = 0; i < withApk.size(); i += batchSize) {
                    List<ApkLinkPlatform> batch = withApk.subList(i, Math.min(i + batchSize, withApk.size()));
                    if (batch.size() == 1) {
                        ApkLinkPlatform p = batch.get(0);
                        String caption = p.getApkFileName() != null && !p.getApkFileName().isEmpty() ? p.getApkFileName() : p.getName();
                        SendDocument doc = new SendDocument();
                        doc.setChatId(chatId.toString());
                        doc.setDocument(p.getApkFileId() != null && !p.getApkFileId().isEmpty() ? new InputFile(p.getApkFileId()) : new InputFile(p.getApkUrl()));
                        doc.setCaption(caption);
                        Message sent = execute(doc);
                        if (i == 0) configService.saveApkChannelMessageLink(sent.getChatId(), sent.getMessageId());
                    } else {
                        List<InputMedia> mediaList = new ArrayList<>();
                        for (ApkLinkPlatform p : batch) {
                            String media = p.getApkFileId() != null && !p.getApkFileId().isEmpty() ? p.getApkFileId() : p.getApkUrl();
                            String caption = p.getApkFileName() != null && !p.getApkFileName().isEmpty() ? p.getApkFileName() : p.getName();
                            InputMediaDocument input = new InputMediaDocument(media);
                            input.setCaption(caption);
                            mediaList.add(input);
                        }
                        SendMediaGroup group = new SendMediaGroup(chatId.toString(), mediaList);
                        List<Message> sent = execute(group);
                        if (!sent.isEmpty() && i == 0) {
                            Message first = sent.get(0);
                            configService.saveApkChannelMessageLink(first.getChatId(), first.getMessageId());
                        }
                    }
                }
            }
        } catch (TelegramApiException e) {
            logger.error("Failed to send APKs in channel: {}", e.getMessage());
        }
    }

    private void handleGroupMessage(String text, Long chatId, Long userId) {
        String trimmed = text != null ? text.trim() : "";
        Optional<ApkLinkBotConfig> configOpt = configService.getConfig();
        String groupKeywordAllApk = configOpt.map(ApkLinkBotConfig::getGroupKeywordAllApk).orElse(null);
        if (groupKeywordAllApk != null && !groupKeywordAllApk.isEmpty() && trimmed.equalsIgnoreCase(groupKeywordAllApk.trim())) {
            int cooldownMinutes = configOpt.map(c -> c.getCooldownGroupMinutes() != null ? c.getCooldownGroupMinutes() : 0).orElse(0);
            boolean isAdmin = isChatAdmin(chatId, userId);
            if (!isAdmin) {
                Optional<Long> remaining = cooldownService.getRemainingMinutesGroup(chatId, cooldownMinutes);
                if (remaining.isPresent()) {
                    sendCooldownMessage(chatId, remaining.get());
                    return;
                }
                cooldownService.applyGroupCooldown(chatId);
            }
            configService.getApkChannelMessageLink()
                    .ifPresentOrElse(link -> sendText(chatId, link),
                            () -> sendText(chatId, getMessage(chatId, "apk_link.link_not_configured")));
            return;
        }
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
        String template = getMessage(chatId, "apk_link.cooldown");
        String text = String.format(template, remainingMinutes);
        sendText(chatId, text);
    }

    private void sendCooldownMessageWithBackButton(Long chatId, long remainingMinutes) {
        String template = getMessage(chatId, "apk_link.cooldown");
        String text = String.format(template, remainingMinutes);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.back"), BACK_MAIN)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendCooldownRedirectToChannel(Long chatId, String channelLink) {
        String text = getMessage(chatId, "apk_link.cooldown_open_channel");
        String buttonText = getMessage(chatId, "apk_link.button.open_channel");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createUrlButton(buttonText, channelLink)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private String getMessage(Long chatId, String code) {
        return messageSource.getMessage(code, null, code, languageService.getLocale(chatId));
    }

    private String getMessage(Long chatId, String code, Object[] args) {
        return messageSource.getMessage(code, args, code, languageService.getLocale(chatId));
    }

    private String getMessage(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
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

    private void sendGroupChannelScreen(Long chatId) {
        List<ApkLinkInvite> channels = inviteService.findAllChannels();
        List<ApkLinkInvite> groups = inviteService.findAllGroups();
        String text = getMessage(chatId, "apk_link.group_channel_prompt");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int maxRows = Math.max(channels.size(), groups.size());
        for (int i = 0; i < maxRows; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            if (i < channels.size()) {
                ApkLinkInvite ch = channels.get(i);
                String channelLabel = "📢 " + (ch.getName() != null ? ch.getName() : "");
                row.add(createUrlButton(channelLabel, ch.getInviteLink()));
            }
            if (i < groups.size()) {
                ApkLinkInvite gr = groups.get(i);
                String groupLabel = "👥 " + (gr.getName() != null ? gr.getName() : "");
                row.add(createUrlButton(groupLabel, gr.getInviteLink()));
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.back"), BACK_MAIN)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private void sendContactsScreen(Long chatId) {
        String text = getMessage(chatId, "contact.message.contact_prompt");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createUrlButton(getMessage(chatId, "contact.button.admin"), "https://t.me/Boss9w")));
        rows.add(List.of(createUrlButton(getMessage(chatId, "contact.button.chat"), "https://t.me/Abadiy_Kassa")));
        rows.add(List.of(createCallbackButton(getMessage(chatId, "apk_link.button.back"), BACK_MAIN)));
        sendMessageWithKeyboard(chatId, text, rows);
    }

    private InlineKeyboardButton createCallbackButton(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private InlineKeyboardButton createUrlButton(String text, String url) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        String link = (url == null || url.isEmpty()) ? "#" : (url.startsWith("http") ? url : "https://" + url);
        btn.setUrl(link);
        return btn;
    }
}
