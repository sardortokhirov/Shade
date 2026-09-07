package com.example.shade.service;

import com.example.shade.model.FeatureSettings;
import com.example.shade.repository.FeatureSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
        FeatureSettings settings = featureSettingsRepository.findLatest()
                .orElseGet(() -> {
                    FeatureSettings defaults = new FeatureSettings();
                    defaults.setTopUpEnabled(true);
                    defaults.setWithdrawEnabled(true);
                    defaults.setBonusEnabled(true);
                    defaults.setWalletEnabled(true);
                    defaults.setPromoEnabled(false);
                    defaults.setBonusLimitEnabled(true);
                    defaults.setPayToggleEnabled(false);
                    defaults.setBonusAutoApproveEnabled(false);
                    defaults.setCreatedAt(LocalDateTime.now());
                    return featureSettingsRepository.save(defaults);
                });

        boolean dirty = false;
        // Existing rows created before wallet_enabled was added can contain NULL.
        // The bot treats NULL as enabled, so normalize the value before returning it
        // to the admin panel to avoid showing Hamyon as red/off by mistake.
        if (settings.getWalletEnabled() == null) {
            settings.setWalletEnabled(true);
            dirty = true;
        }
        if (settings.getPromoEnabled() == null) {
            settings.setPromoEnabled(false);
            dirty = true;
        }
        if (settings.getBonusLimitEnabled() == null) {
            settings.setBonusLimitEnabled(true);
            dirty = true;
        }
        if (settings.getPayToggleEnabled() == null) {
            settings.setPayToggleEnabled(false);
            dirty = true;
        }
        if (dirty) {
            settings = featureSettingsRepository.save(settings);
        }
        return settings;
    }

    private FeatureSettings copyFromCurrent(FeatureSettings current) {
        FeatureSettings settings = new FeatureSettings();
        settings.setTopUpEnabled(current.getTopUpEnabled());
        settings.setWithdrawEnabled(current.getWithdrawEnabled());
        settings.setBonusEnabled(current.getBonusEnabled());
        settings.setWalletEnabled(effectiveWalletEnabled(current));
        settings.setPromoEnabled(current.getPromoEnabled() != null && current.getPromoEnabled());
        settings.setBonusLimitEnabled(current.getBonusLimitEnabled() == null || current.getBonusLimitEnabled());
        settings.setPayToggleEnabled(current.getPayToggleEnabled() != null && current.getPayToggleEnabled());
        settings.setBonusAutoApproveEnabled(current.getBonusAutoApproveEnabled() != null
                && current.getBonusAutoApproveEnabled());
        settings.setCreatedAt(LocalDateTime.now());
        return settings;
    }

    @Transactional
    public void toggleTopUp(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setTopUpEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Top-up {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleWithdraw(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setWithdrawEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Withdraw {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleBonus(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setBonusEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Bonus {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleWallet(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setWalletEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Wallet {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void togglePromo(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setPromoEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Promo {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleBonusLimit(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setBonusLimitEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Bonus limit {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void togglePayToggle(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setPayToggleEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Pay toggle {} globally", enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void toggleBonusAutoApprove(boolean enabled) {
        FeatureSettings current = getGlobalSettings();
        FeatureSettings settings = copyFromCurrent(current);
        settings.setBonusAutoApproveEnabled(enabled);
        featureSettingsRepository.save(settings);
        logger.info("Bonus auto-approve {} globally", enabled ? "enabled" : "disabled");
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

    public boolean canPerformWallet() {
        Boolean v = getGlobalSettings().getWalletEnabled();
        return v == null || v;
    }

    public boolean isPromoEnabled() {
        Boolean v = getGlobalSettings().getPromoEnabled();
        return v != null && v;
    }

    public boolean isBonusLimitEnabled() {
        Boolean enabled = getGlobalSettings().getBonusLimitEnabled();
        return enabled == null || enabled;
    }

    public boolean isPayToggleEnabled() {
        Boolean v = getGlobalSettings().getPayToggleEnabled();
        return v != null && v;
    }

    public boolean isBonusAutoApproveEnabled() {
        Boolean enabled = getGlobalSettings().getBonusAutoApproveEnabled();
        return enabled != null && enabled;
    }

    private boolean effectiveWalletEnabled(FeatureSettings settings) {
        Boolean value = settings.getWalletEnabled();
        return value == null || value;
    }
}
