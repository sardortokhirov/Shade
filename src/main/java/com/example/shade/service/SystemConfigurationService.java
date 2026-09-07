package com.example.shade.service;

import com.example.shade.model.SystemConfiguration;
import com.example.shade.model.UzcardRail;
import com.example.shade.repository.SystemConfigurationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SystemConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationService.class);
    private final SystemConfigurationRepository configurationRepository;
    private static final long CACHE_TTL_MS = 30_000L;
    private volatile SystemConfiguration cachedConfig;
    private volatile long cacheTimestamp;

    private static final Long DEFAULT_TOP_UP_MIN = 5_000L;
    private static final Long DEFAULT_TOP_UP_MAX = 10_000_000L;
    private static final BigDecimal DEFAULT_BONUS_TOP_UP_MIN = new BigDecimal("3600");
    private static final BigDecimal DEFAULT_BONUS_TOP_UP_MAX = new BigDecimal("100000");
    private static final Long DEFAULT_MIN_TICKETS = 5L;
    private static final Long DEFAULT_MAX_TICKETS = 400L;
    private static final BigDecimal DEFAULT_REFERRAL_COMMISSION = new BigDecimal("0.001");
    private static final BigDecimal DEFAULT_WITHDRAW_FEE_PERCENTAGE = new BigDecimal("1.00");
    private static final Long DEFAULT_TICKET_CALCULATION = 10_000L;
    private static final Long DEFAULT_WALLET_MIN_WITHDRAW = 10_000L;
    private static final Long DEFAULT_WALLET_WITHDRAW_RATIO = 1L;
    private static final Long DEFAULT_WALLET_TRANSFER_MIN = 5_000L;
    private static final Long DEFAULT_WALLET_TRANSFER_MAX = 10_000_000L;
    private static final BigDecimal DEFAULT_WALLET_TO_WALLET_FEE = BigDecimal.ZERO;
    private static final Long DEFAULT_DAILY_BONUS_TRANSFER_LIMIT = 100_000L;
    private static final BigDecimal DEFAULT_TOP_UP_DAILY_LIMIT_INCREASE_PERCENTAGE = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_DEPOSIT_DAILY_LIMIT_INCREASE_PERCENTAGE = BigDecimal.ZERO;
    private static final Boolean DEFAULT_HUMO_ENABLED = true;
    private static final UzcardRail DEFAULT_UZCARD_RAIL = UzcardRail.OSON;
    private static final Long DEFAULT_LOTTERY_COOLDOWN_SECONDS = 300L;

    @Transactional
    public SystemConfiguration getConfiguration() {
        long now = System.currentTimeMillis();
        SystemConfiguration cached = cachedConfig;
        if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return cached;
        }
        synchronized (this) {
            cached = cachedConfig;
            if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) {
                return cached;
            }
            SystemConfiguration loadedConfig = configurationRepository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(() -> {
                    SystemConfiguration config = new SystemConfiguration();
                    config.setTopUpMinAmount(DEFAULT_TOP_UP_MIN);
                    config.setTopUpMaxAmount(DEFAULT_TOP_UP_MAX);
                    config.setBonusTopUpMinAmount(DEFAULT_BONUS_TOP_UP_MIN);
                    config.setBonusTopUpMaxAmount(DEFAULT_BONUS_TOP_UP_MAX);
                    config.setMinTickets(DEFAULT_MIN_TICKETS);
                    config.setMaxTickets(DEFAULT_MAX_TICKETS);
                    config.setReferralCommissionPercentage(DEFAULT_REFERRAL_COMMISSION);
                    config.setWithdrawFeePercentage(DEFAULT_WITHDRAW_FEE_PERCENTAGE);
                    config.setTicketCalculationAmount(DEFAULT_TICKET_CALCULATION);
                    config.setWalletToWalletFeePercentage(DEFAULT_WALLET_TO_WALLET_FEE);
                    config.setDailyBonusTransferLimit(DEFAULT_DAILY_BONUS_TRANSFER_LIMIT);
                    config.setTopUpDailyLimitIncreasePercentage(DEFAULT_TOP_UP_DAILY_LIMIT_INCREASE_PERCENTAGE);
                    config.setDepositDailyLimitIncreasePercentage(DEFAULT_DEPOSIT_DAILY_LIMIT_INCREASE_PERCENTAGE);
                    config.setHumoEnabled(DEFAULT_HUMO_ENABLED);
                    config.setUzcardRail(DEFAULT_UZCARD_RAIL);
                    config.setLotteryCooldownSeconds(DEFAULT_LOTTERY_COOLDOWN_SECONDS);
                    config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    return configurationRepository.save(config);
                });
            cachedConfig = loadedConfig;
            cacheTimestamp = now;
            return loadedConfig;
        }
    }

    @Transactional
    public SystemConfiguration updateConfiguration(SystemConfiguration config) {
        if (config.getWalletToWalletFeePercentage() != null) {
            BigDecimal fee = config.getWalletToWalletFeePercentage();
            if (fee.compareTo(BigDecimal.ZERO) < 0 || fee.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Wallet-to-wallet fee must be between 0 and 1");
            }
        }
        if (config.getHumoEnabled() == null) {
            config.setHumoEnabled(getHumoEnabled());
        }
        if (config.getUzcardRail() == null) {
            config.setUzcardRail(getUzcardRail());
        }
        config.setHumoLegacyDualCheckEnd(null);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateCache();
        logger.info("System configuration updated: {}", saved.getId());
        return saved;
    }

    private void invalidateCache() {
        cachedConfig = null;
        cacheTimestamp = 0L;
    }

    public Long getTopUpMinAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getTopUpMinAmount() != null ? config.getTopUpMinAmount() : DEFAULT_TOP_UP_MIN;
    }

    public Long getTopUpMaxAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getTopUpMaxAmount() != null ? config.getTopUpMaxAmount() : DEFAULT_TOP_UP_MAX;
    }

    public BigDecimal getBonusTopUpMinAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getBonusTopUpMinAmount() != null ? config.getBonusTopUpMinAmount() : DEFAULT_BONUS_TOP_UP_MIN;
    }

    public BigDecimal getBonusTopUpMaxAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getBonusTopUpMaxAmount() != null ? config.getBonusTopUpMaxAmount() : DEFAULT_BONUS_TOP_UP_MAX;
    }

    public Long getMinTickets() {
        SystemConfiguration config = getConfiguration();
        return config.getMinTickets() != null ? config.getMinTickets() : DEFAULT_MIN_TICKETS;
    }

    public Long getMaxTickets() {
        SystemConfiguration config = getConfiguration();
        return config.getMaxTickets() != null ? config.getMaxTickets() : DEFAULT_MAX_TICKETS;
    }

    public BigDecimal getReferralCommissionPercentage() {
        SystemConfiguration config = getConfiguration();
        return config.getReferralCommissionPercentage() != null
                ? config.getReferralCommissionPercentage()
                : DEFAULT_REFERRAL_COMMISSION;
    }

    /**
     * Effective withdraw fee percent (0–100). Null in DB defaults to 1% (legacy behavior was hardcoded 0.99 multiplier).
     */
    public BigDecimal getWithdrawFeePercentage() {
        SystemConfiguration config = getConfiguration();
        BigDecimal v = config.getWithdrawFeePercentage() != null
                ? config.getWithdrawFeePercentage()
                : DEFAULT_WITHDRAW_FEE_PERCENTAGE;
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return v;
    }

    /**
     * Multiplier applied to gross withdrawal amount to get net UZS credited (after fee). E.g. fee 1% → 0.99.
     */
    public BigDecimal getWithdrawNetMultiplier() {
        BigDecimal fee = getWithdrawFeePercentage();
        return BigDecimal.valueOf(100).subtract(fee)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
    }

    public Long getTicketCalculationAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getTicketCalculationAmount() != null
                ? config.getTicketCalculationAmount()
                : DEFAULT_TICKET_CALCULATION;
    }

    public Long getWalletMinWithdrawAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getWalletMinWithdrawAmount() != null
                ? config.getWalletMinWithdrawAmount()
                : DEFAULT_WALLET_MIN_WITHDRAW;
    }

    public Long getWalletWithdrawRatio() {
        SystemConfiguration config = getConfiguration();
        return config.getWalletWithdrawRatio() != null
                ? config.getWalletWithdrawRatio()
                : DEFAULT_WALLET_WITHDRAW_RATIO;
    }

    public Long getWalletTransferMinAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getWalletTransferMinAmount() != null
                ? config.getWalletTransferMinAmount()
                : DEFAULT_WALLET_TRANSFER_MIN;
    }

    public Long getWalletTransferMaxAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getWalletTransferMaxAmount() != null
                ? config.getWalletTransferMaxAmount()
                : DEFAULT_WALLET_TRANSFER_MAX;
    }

    @Transactional
    public SystemConfiguration setWalletWithdrawRatio(Long ratio) {
        SystemConfiguration config = getConfiguration();
        config.setWalletWithdrawRatio(ratio);
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateCache();
        return saved;
    }

    @Transactional
    public SystemConfiguration setWalletMinWithdrawAmount(Long amount) {
        SystemConfiguration config = getConfiguration();
        config.setWalletMinWithdrawAmount(amount);
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateCache();
        return saved;
    }

    @Transactional
    public SystemConfiguration setWalletTransferMinAmount(Long amount) {
        SystemConfiguration config = getConfiguration();
        config.setWalletTransferMinAmount(amount);
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateCache();
        return saved;
    }

    @Transactional
    public SystemConfiguration setWalletTransferMaxAmount(Long amount) {
        SystemConfiguration config = getConfiguration();
        config.setWalletTransferMaxAmount(amount);
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateCache();
        return saved;
    }

    public BigDecimal getWalletToWalletFeePercentage() {
        SystemConfiguration config = getConfiguration();
        BigDecimal pct = config.getWalletToWalletFeePercentage();
        if (pct == null) {
            return DEFAULT_WALLET_TO_WALLET_FEE;
        }
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(BigDecimal.ONE) > 0) {
            logger.warn("Ignoring invalid wallet-to-wallet fee {} (must be 0..1); using 0", pct);
            return DEFAULT_WALLET_TO_WALLET_FEE;
        }
        return pct;
    }

    @Transactional
    public SystemConfiguration setWalletToWalletFeePercentage(BigDecimal percentage) {
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0
                || percentage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Wallet-to-wallet fee must be between 0 and 1");
        }
        SystemConfiguration config = getConfiguration();
        config.setWalletToWalletFeePercentage(percentage);
        SystemConfiguration saved = configurationRepository.save(config);
        invalidateCache();
        logger.info("Wallet-to-wallet fee percentage updated to {}", percentage);
        return saved;
    }

    public Long getDailyBonusTransferLimit() {
        SystemConfiguration config = getConfiguration();
        return config.getDailyBonusTransferLimit() != null
                ? config.getDailyBonusTransferLimit()
                : DEFAULT_DAILY_BONUS_TRANSFER_LIMIT;
    }

    public BigDecimal getTopUpDailyLimitIncreasePercentage() {
        SystemConfiguration config = getConfiguration();
        return config.getTopUpDailyLimitIncreasePercentage() != null
                ? config.getTopUpDailyLimitIncreasePercentage()
                : DEFAULT_TOP_UP_DAILY_LIMIT_INCREASE_PERCENTAGE;
    }

    public BigDecimal getDepositDailyLimitIncreasePercentage() {
        SystemConfiguration config = getConfiguration();
        return config.getDepositDailyLimitIncreasePercentage() != null
                ? config.getDepositDailyLimitIncreasePercentage()
                : DEFAULT_DEPOSIT_DAILY_LIMIT_INCREASE_PERCENTAGE;
    }

    public Boolean getHumoEnabled() {
        SystemConfiguration config = getConfiguration();
        return config.getHumoEnabled() != null ? config.getHumoEnabled() : DEFAULT_HUMO_ENABLED;
    }

    public UzcardRail getUzcardRail() {
        SystemConfiguration config = getConfiguration();
        return config.getUzcardRail() != null ? config.getUzcardRail() : DEFAULT_UZCARD_RAIL;
    }

    public Long getLotteryCooldownSeconds() {
        SystemConfiguration config = getConfiguration();
        return config.getLotteryCooldownSeconds() != null
                ? config.getLotteryCooldownSeconds()
                : DEFAULT_LOTTERY_COOLDOWN_SECONDS;
    }

    @Transactional
    public void setHumoEnabled(boolean enabled) {
        SystemConfiguration config = getConfiguration();
        config.setHumoEnabled(enabled);
        configurationRepository.save(config);
        invalidateCache();
        logger.info("HUMO enabled set to {}", enabled);
    }

    @Transactional
    public void setUzcardRail(UzcardRail rail) {
        if (rail == null) {
            rail = DEFAULT_UZCARD_RAIL;
        }
        SystemConfiguration config = getConfiguration();
        config.setUzcardRail(rail);
        configurationRepository.save(config);
        invalidateCache();
        logger.info("UZCARD rail set to {}", rail);
    }
}
