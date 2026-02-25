package com.example.shade.service;

import com.example.shade.model.ApkLinkBotConfig;
import com.example.shade.repository.ApkLinkBotConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApkLinkBotConfigService {

    private final ApkLinkBotConfigRepository configRepository;

    public Optional<ApkLinkBotConfig> getConfig() {
        return configRepository.findAll().stream().findFirst();
    }

    @Transactional
    public ApkLinkBotConfig saveConfig(String botToken, Integer cooldownPrivateMinutes, Integer cooldownGroupMinutes,
            String channelKeywordAllApk, String groupKeywordAllApk,
            Long apkChannelChatId, Integer apkChannelMessageId, Integer autoPostIntervalHours,
            Integer groupUserLinkLimit, Integer groupUserApkLimit, Integer groupUserFreezeMinutes) {
        ApkLinkBotConfig config = getConfig().orElse(ApkLinkBotConfig.builder().build());
        config.setBotToken(botToken != null ? botToken : config.getBotToken());
        config.setCooldownPrivateMinutes(
                cooldownPrivateMinutes != null ? cooldownPrivateMinutes : config.getCooldownPrivateMinutes());
        config.setCooldownGroupMinutes(
                cooldownGroupMinutes != null ? cooldownGroupMinutes : config.getCooldownGroupMinutes());
        if (channelKeywordAllApk != null)
            config.setChannelKeywordAllApk(channelKeywordAllApk);
        if (groupKeywordAllApk != null)
            config.setGroupKeywordAllApk(groupKeywordAllApk);
        if (apkChannelChatId != null)
            config.setApkChannelChatId(apkChannelChatId);
        if (apkChannelMessageId != null)
            config.setApkChannelMessageId(apkChannelMessageId);
        if (autoPostIntervalHours != null)
            config.setAutoPostIntervalHours(autoPostIntervalHours);
        if (groupUserLinkLimit != null)
            config.setGroupUserLinkLimit(groupUserLinkLimit);
        if (groupUserApkLimit != null)
            config.setGroupUserApkLimit(groupUserApkLimit);
        if (groupUserFreezeMinutes != null)
            config.setGroupUserFreezeMinutes(groupUserFreezeMinutes);
        return configRepository.save(config);
    }

    /**
     * Saves the channel message link (used by bot after posting all APKs in
     * channel).
     * Does not change other config fields.
     * If mainApkChannelChatId is null, sets it to this chatId so the first channel
     * becomes main.
     */
    @Transactional
    public ApkLinkBotConfig saveApkChannelMessageLink(Long chatId, Integer messageId) {
        ApkLinkBotConfig config = getConfig().orElse(ApkLinkBotConfig.builder().build());
        config.setApkChannelChatId(chatId);
        config.setApkChannelMessageId(messageId);
        if (config.getMainApkChannelChatId() == null) {
            config.setMainApkChannelChatId(chatId);
        }
        return configRepository.save(config);
    }

    /**
     * Sets or clears the main APK channel. Only this channel may trigger
     * send-all-APKs when the keyword is posted.
     */
    @Transactional
    public ApkLinkBotConfig setMainApkChannelChatId(Long mainApkChannelChatId) {
        ApkLinkBotConfig config = getConfig().orElse(ApkLinkBotConfig.builder().build());
        config.setMainApkChannelChatId(mainApkChannelChatId);
        return configRepository.save(config);
    }

    /**
     * Builds the t.me link for the stored APK channel message.
     * Format for supergroup/channel:
     * https://t.me/c/&lt;chat_id_without_-100&gt;/&lt;message_id&gt;
     * e.g. chatId -1001234567890 -> 1234567890.
     */
    public Optional<String> buildApkChannelMessageLink(Long chatId, Integer messageId) {
        if (chatId == null || messageId == null)
            return Optional.empty();
        long abs = Math.abs(chatId.longValue());
        if (abs >= 1_000_000_000_000L) {
            abs = abs % 1_000_000_000_000L;
        }
        return Optional.of("https://t.me/c/" + abs + "/" + messageId);
    }

    public Optional<String> getApkChannelMessageLink() {
        return getConfig()
                .filter(c -> c.getApkChannelChatId() != null && c.getApkChannelMessageId() != null)
                .flatMap(c -> buildApkChannelMessageLink(c.getApkChannelChatId(), c.getApkChannelMessageId()));
    }

    public String getBotToken() {
        return getConfig().map(ApkLinkBotConfig::getBotToken).orElse(null);
    }

    public static String maskToken(String token) {
        if (token == null || token.length() < 8)
            return "***";
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
