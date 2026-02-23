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
    public ApkLinkBotConfig saveConfig(String botToken, Integer cooldownPrivateMinutes, Integer cooldownGroupMinutes) {
        ApkLinkBotConfig config = getConfig().orElse(ApkLinkBotConfig.builder().build());
        config.setBotToken(botToken != null ? botToken : config.getBotToken());
        config.setCooldownPrivateMinutes(cooldownPrivateMinutes != null ? cooldownPrivateMinutes : config.getCooldownPrivateMinutes());
        config.setCooldownGroupMinutes(cooldownGroupMinutes != null ? cooldownGroupMinutes : config.getCooldownGroupMinutes());
        return configRepository.save(config);
    }

    public String getBotToken() {
        return getConfig().map(ApkLinkBotConfig::getBotToken).orElse(null);
    }

    public static String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
