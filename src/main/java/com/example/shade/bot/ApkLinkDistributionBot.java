package com.example.shade.bot;

import com.example.shade.model.ApkLinkBotConfig;
import com.example.shade.model.ApkLinkInvite;
import com.example.shade.model.ApkLinkPlatform;
import com.example.shade.service.ApkLinkBotConfigService;
import com.example.shade.service.ApkLinkCooldownService;
import com.example.shade.service.ApkDownloadService;
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

import java.io.ByteArrayInputStream;
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
    private final ApkDownloadService apkDownloadService;
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
                    if (update.getMessage().getFrom() != null) {
                        handleGroupMessage(update.getMessage().getText(), chatId,
                                update.getMessage().getFrom().getId());
                    }
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
            return;
        }
        sendText(chatId, getMessage(chatId, "apk_link.unknown_action"));
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
        row.add(createCallbackButton(getMessage(chatId, "apk_link.button.link"), PREFIX_SEND_LINK + platformId));
        Optional<String> channelLink = configService.getApkChannelMessageLink();
        if (channelLink.isPresent()) {
            row.add(createUrlButton(getMessage(chatId, "apk_link.button.apk"), channelLink.get()));
        } else {
            row.add(createCallbackButton(getMessage(chatId, "apk_link.button.apk"), PREFIX_SEND_APK + platformId));
        }
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
        if (remaining.isPresent() && remaining.get() > 0) {
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
        // APK in private: no cooldown. When main channel link is set, always redirect to that channel message.
        Optional<String> channelLink = configService.getApkChannelMessageLink();
        if (channelLink.isPresent()) {
            sendApkRedirectToChannel(chatId, channelLink.get());
            return;
        }
        // No channel link: send platform's APK file/URL or fallback (no cooldown for APK)
        Optional<ApkLinkPlatform> platform = platformService.findPlatformById(platformId);
        if (platform.isEmpty()) {
            sendText(chatId, getMessage(chatId, "apk_link.platform_not_found"));
            return;
        }
        ApkLinkPlatform p = platform.get();
        if (p.getApkFileId() != null && !p.getApkFileId().isEmpty()) {
            sendDocumentByFileId(chatId, p.getApkFileId(), captionFor(p));
        } else if (p.getApkUrl() != null && !p.getApkUrl().isEmpty()) {
            Optional<byte[]> data = apkDownloadService.downloadApk(p.getApkUrl());
            if (data.isPresent()) {
                Optional<Message> sent = sendDocumentFromBytes(chatId, data.get(), fileNameFor(p), captionFor(p));
                if (sent.isPresent() && sent.get().getDocument() != null) {
                    platformService.updateApkFileId(p.getId(), sent.get().getDocument().getFileId());
                }
            } else {
                sendText(chatId, p.getApkUrl());
            }
        } else {
            sendText(chatId, getMessage(chatId, "apk_link.apk_not_configured"));
        }
    }

    private void handleChannelMessage(String text, Long chatId) {
        if (text == null) return;
        String keyword = configService.getConfig()
                .map(ApkLinkBotConfig::getChannelKeywordAllApk)
                .orElse(null);
        if (keyword == null || keyword.isEmpty()) return;
        String normalizedInput = text.trim().toLowerCase();
        String normalizedKeyword = keyword.trim().toLowerCase();
        if (normalizedInput.startsWith("/")) normalizedInput = normalizedInput.substring(1);
        if (!normalizedInput.equals(normalizedKeyword)) {
            return;
        }
        Long mainChatId = configService.getConfig()
                .map(ApkLinkBotConfig::getMainApkChannelChatId)
                .orElse(null);
        if (mainChatId != null && !mainChatId.equals(chatId)) {
            sendText(chatId, getMessage(chatId, "apk_link.wrong_channel"));
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
            boolean channelLinkSaved = false;
            if (withApk.size() == 1) {
                ApkLinkPlatform p = withApk.get(0);
                String caption = captionFor(p);
                if (p.getApkFileId() != null && !p.getApkFileId().isEmpty()) {
                    SendDocument doc = new SendDocument();
                    doc.setChatId(chatId.toString());
                    doc.setDocument(new InputFile(p.getApkFileId()));
                    doc.setCaption(caption);
                    Message sent = execute(doc);
                    configService.saveApkChannelMessageLink(sent.getChatId(), sent.getMessageId());
                } else if (p.getApkUrl() != null && !p.getApkUrl().isEmpty()) {
                    Optional<byte[]> data = apkDownloadService.downloadApk(p.getApkUrl());
                    if (data.isPresent()) {
                        Optional<Message> sent = sendDocumentFromBytes(chatId, data.get(), fileNameFor(p), caption);
                        if (sent.isPresent() && sent.get().getDocument() != null) {
                            configService.saveApkChannelMessageLink(sent.get().getChatId(), sent.get().getMessageId());
                            platformService.updateApkFileId(p.getId(), sent.get().getDocument().getFileId());
                        }
                    } else {
                        sendText(chatId, p.getApkUrl());
                    }
                }
            } else {
                final int batchSize = 10;
                List<ApkLinkPlatform> byFileId = new ArrayList<>();
                for (ApkLinkPlatform p : withApk) {
                    if (p.getApkFileId() != null && !p.getApkFileId().isEmpty()) {
                        byFileId.add(p);
                    } else if (p.getApkUrl() != null && !p.getApkUrl().isEmpty()) {
                        Optional<byte[]> data = apkDownloadService.downloadApk(p.getApkUrl());
                        if (data.isPresent()) {
                            Optional<Message> sent = sendDocumentFromBytes(chatId, data.get(), fileNameFor(p), captionFor(p));
                            if (sent.isPresent() && sent.get().getDocument() != null) {
                                if (!channelLinkSaved) {
                                    configService.saveApkChannelMessageLink(sent.get().getChatId(), sent.get().getMessageId());
                                    channelLinkSaved = true;
                                }
                                platformService.updateApkFileId(p.getId(), sent.get().getDocument().getFileId());
                            }
                        } else {
                            sendText(chatId, p.getApkUrl());
                        }
                    }
                }
                for (int i = 0; i < byFileId.size(); i += batchSize) {
                    List<ApkLinkPlatform> batch = byFileId.subList(i, Math.min(i + batchSize, byFileId.size()));
                    if (batch.size() == 1) {
                        ApkLinkPlatform p = batch.get(0);
                        SendDocument doc = new SendDocument();
                        doc.setChatId(chatId.toString());
                        doc.setDocument(new InputFile(p.getApkFileId()));
                        doc.setCaption(captionFor(p));
                        Message sent = execute(doc);
                        if (!channelLinkSaved) {
                            configService.saveApkChannelMessageLink(sent.getChatId(), sent.getMessageId());
                            channelLinkSaved = true;
                        }
                    } else {
                        List<InputMedia> mediaList = new ArrayList<>();
                        for (ApkLinkPlatform p : batch) {
                            InputMediaDocument input = new InputMediaDocument(p.getApkFileId());
                            input.setCaption(captionFor(p));
                            mediaList.add(input);
                        }
                        SendMediaGroup group = new SendMediaGroup(chatId.toString(), mediaList);
                        List<Message> sent = execute(group);
                        if (!sent.isEmpty() && !channelLinkSaved) {
                            configService.saveApkChannelMessageLink(sent.get(0).getChatId(), sent.get(0).getMessageId());
                            channelLinkSaved = true;
                        }
                    }
                }
            }
        } catch (TelegramApiException e) {
            logger.error("Failed to send APKs in channel: {}", e.getMessage());
        }
    }

    private static String captionFor(ApkLinkPlatform p) {
        return p.getApkFileName() != null && !p.getApkFileName().isEmpty() ? p.getApkFileName() : p.getName();
    }

    private static String fileNameFor(ApkLinkPlatform p) {
        if (p.getApkFileName() != null && !p.getApkFileName().isEmpty()) return p.getApkFileName();
        return p.getName() != null ? p.getName() : "platform";
    }

    private void handleGroupMessage(String text, Long chatId, Long userId) {
        String trimmed = text != null ? text.trim() : "";
        String normalizedGroupInput = trimmed;
        if (normalizedGroupInput.toLowerCase().startsWith("/")) {
            normalizedGroupInput = normalizedGroupInput.substring(1).trim();
        }
        Optional<ApkLinkBotConfig> configOpt = configService.getConfig();
        String groupKeywordAllApk = configOpt.map(ApkLinkBotConfig::getGroupKeywordAllApk).orElse(null);
        if (groupKeywordAllApk != null && !groupKeywordAllApk.isEmpty() && normalizedGroupInput.equalsIgnoreCase(groupKeywordAllApk.trim())) {
            int cooldownMinutes = configOpt.map(c -> c.getCooldownGroupMinutes() != null ? c.getCooldownGroupMinutes() : 0).orElse(0);
            boolean isAdmin = isChatAdmin(chatId, userId);
            if (!isAdmin) {
                Optional<Long> remaining = cooldownService.getRemainingMinutesGroup(chatId, cooldownMinutes);
                if (remaining.isPresent() && remaining.get() > 0) {
                    sendCooldownMessage(chatId, remaining.get());
                    return;
                }
                cooldownService.applyGroupCooldown(chatId);
            }
            configService.getApkChannelMessageLink()
                    .ifPresentOrElse(link -> sendChannelLinkButton(chatId, link),
                            () -> sendText(chatId, getMessage(chatId, "apk_link.link_not_configured")));
            return;
        }
        Optional<ApkLinkPlatform> platformOpt = platformService.findPlatformByKeyword(normalizedGroupInput);
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
            if (remaining.isPresent() && remaining.get() > 0) {
                sendCooldownMessage(chatId, remaining.get());
                return;
            }
            cooldownService.applyGroupCooldown(chatId);
        }
        sendText(chatId, platform.getLinkUrl());
        if (platform.getApkFileId() != null && !platform.getApkFileId().isEmpty()) {
            sendDocumentByFileId(chatId, platform.getApkFileId(), captionFor(platform));
        } else if (platform.getApkUrl() != null && !platform.getApkUrl().isEmpty()) {
            Optional<byte[]> data = apkDownloadService.downloadApk(platform.getApkUrl());
            if (data.isPresent()) {
                Optional<Message> sent = sendDocumentFromBytes(chatId, data.get(), fileNameFor(platform), captionFor(platform));
                if (sent.isPresent() && sent.get().getDocument() != null) {
                    platformService.updateApkFileId(platform.getId(), sent.get().getDocument().getFileId());
                }
            } else {
                sendText(chatId, platform.getApkUrl());
            }
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

    private void sendApkRedirectToChannel(Long chatId, String channelLink) {
        sendChannelLinkButton(chatId, channelLink);
    }

    /** Sends a message with a single URL button that opens the channel APK message (private or group). */
    private void sendChannelLinkButton(Long chatId, String channelLink) {
        String text = getMessage(chatId, "apk_link.apk_redirect_to_channel");
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

    /**
     * Sends a document from downloaded bytes (e.g. after fetching from redirect/tracking URL).
     *
     * @return the sent Message if successful (to extract file_id for persistence), or empty on failure
     */
    private Optional<Message> sendDocumentFromBytes(Long chatId, byte[] data, String fileName, String caption) {
        if (data == null || data.length == 0) return Optional.empty();
        String safeName = sanitizeFileName(fileName != null ? fileName : "file.apk");
        if (!safeName.toLowerCase().endsWith(".apk")) safeName = safeName + ".apk";
        try {
            SendDocument doc = new SendDocument();
            doc.setChatId(chatId.toString());
            doc.setDocument(new InputFile(new ByteArrayInputStream(data), safeName));
            if (caption != null && !caption.isEmpty()) {
                doc.setCaption(caption);
            }
            return Optional.of(execute(doc));
        } catch (TelegramApiException e) {
            logger.error("Failed to send document from bytes: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "file.apk";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
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
