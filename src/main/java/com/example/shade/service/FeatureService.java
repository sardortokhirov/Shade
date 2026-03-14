package com.example.shade.service;

import com.example.shade.model.FeatureSettings;
import com.example.shade.repository.FeatureSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Date-8/11/2025
 * By Sardor Tokhirov
 * Time-4:45 AM (GMT+5)
 */

@Service
@RequiredArgsConstructor
public class FeatureService {
    private static final Logger logger = LoggerFactory.getLogger(FeatureService.class);
    private final FeatureSettingsRepository featureSettingsRepository;

    @Transactional
    public FeatureSettings getGlobalSettings() {
        return featureSettingsRepository.findLatest()
                .orElseGet(() -> {
                    FeatureSettings settings = new FeatureSettings();
                    settings.setTopUpEnabled(true);
                    settings.setWithdrawEnabled(true);
                    settings.setBonusEnabled(true);
                    settings.setWalletEnabled(true);
                    settings.setPromoEnabled(false);
                    settings.setBonusLimitEnabled(true);
                    settings.setPayToggleEnabled(false);
                    settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    return featureSettingsRepository.save(settings);
                });
    }

    @Transactional
    public void toggleTopUp(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(enabled);
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(current.getWalletEnabled());
        settings.setPromoEnabled(current.getPromoEnabled());
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(current.getPayToggleEnabled());
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Top-up {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleWithdraw(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(enabled);
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(current.getWalletEnabled());
        settings.setPromoEnabled(current.getPromoEnabled());
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(current.getPayToggleEnabled());
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Withdraw {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleBonus(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(enabled);
        settings.setWalletEnabled(current.getWalletEnabled());
        settings.setPromoEnabled(current.getPromoEnabled());
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(current.getPayToggleEnabled());
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Bonus {} globally", enabled ? "enabled" : "disabled");
    }

    public boolean canPerformTopUp() {
        return getGlobalSettings().getTopUpEnabled();
    }

    public boolean canPerformWithdraw() {
        return getGlobalSettings().getWithdrawEnabled();
    }

    public boolean canPerformBonus() {
        return getGlobalSettings().getBonusEnabled();
    }

    @Transactional
    public void toggleWallet(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(enabled);
        settings.setPromoEnabled(current.getPromoEnabled());
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(current.getPayToggleEnabled());
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Wallet {} globally", enabled ? "enabled" : "disabled");
    }

    public boolean canPerformWallet() {
        return getGlobalSettings().getWalletEnabled() != null && getGlobalSettings().getWalletEnabled();
    }

    @Transactional
    public void togglePromo(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(current.getWalletEnabled());
        settings.setPromoEnabled(enabled);
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(current.getPayToggleEnabled());
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Promo {} globally", enabled ? "enabled" : "disabled");
    }

    public boolean isPromoEnabled() {
        return getGlobalSettings().getPromoEnabled();
    }

    @Transactional
    public void toggleBonusLimit(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(current.getWalletEnabled());
        settings.setPromoEnabled(current.getPromoEnabled());
        settings.setBonusLimitEnabled(enabled);
        settings.setPayToggleEnabled(current.getPayToggleEnabled());
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Bonus limit {} globally", enabled ? "enabled" : "disabled");
    }

    public boolean isBonusLimitEnabled() {
        Boolean enabled = getGlobalSettings().getBonusLimitEnabled();
        return enabled == null || enabled;
    }

    @Transactional
    public void togglePayToggle(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(current.getWalletEnabled());
        settings.setPromoEnabled(current.getPromoEnabled());
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(enabled);
        settings.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        featureSettingsRepository.save(settings);
        logger.info("Pay toggle {} globally", enabled ? "enabled" : "disabled");
    }

    public boolean isPayToggleEnabled() {
        return getGlobalSettings().getPayToggleEnabled();
    }
}