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
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Service for managing system configuration values
 */
@Service
@RequiredArgsConstructor
public class SystemConfigurationService {
    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationService.class);
    private final SystemConfigurationRepository configurationRepository;

    // Default values
    private static final Long DEFAULT_TOP_UP_MIN = 5_000L;
    private static final Long DEFAULT_TOP_UP_MAX = 10_000_000L;
    private static final BigDecimal DEFAULT_BONUS_TOP_UP_MIN = new BigDecimal("3600");
    private static final BigDecimal DEFAULT_BONUS_TOP_UP_MAX = new BigDecimal("100000");
    private static final Long DEFAULT_MIN_TICKETS = 5L;
    private static final Long DEFAULT_MAX_TICKETS = 400L;
    private static final BigDecimal DEFAULT_WITHDRAWAL_COMMISSION = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_REFERRAL_COMMISSION = new BigDecimal("0.001");
    private static final Long DEFAULT_TICKET_CALCULATION = 10_000L;
    private static final Long DEFAULT_DAILY_BONUS_TRANSFER_LIMIT = 100_000L;
    private static final BigDecimal DEFAULT_TOP_UP_DAILY_LIMIT_INCREASE_PERCENTAGE = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_DEPOSIT_DAILY_LIMIT_INCREASE_PERCENTAGE = BigDecimal.ZERO;
    private static final Boolean DEFAULT_HUMO_ENABLED = true;
    private static final UzcardRail DEFAULT_UZCARD_RAIL = UzcardRail.OSON;
    private static final Long DEFAULT_LOTTERY_COOLDOWN_SECONDS = 300L;
    private static final Long DEFAULT_WALLET_MIN_WITHDRAW_AMOUNT = 10_000L;
    private static final Long DEFAULT_WALLET_WITHDRAW_RATIO = 10L;
    private static final Long DEFAULT_WALLET_TRANSFER_MIN = 5_000L;
    private static final Long DEFAULT_WALLET_TRANSFER_MAX = 10_000_000L;

    @Transactional
    public SystemConfiguration getConfiguration() {
        return configurationRepository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(() -> {
                    SystemConfiguration config = new SystemConfiguration();
                    config.setTopUpMinAmount(DEFAULT_TOP_UP_MIN);
                    config.setTopUpMaxAmount(DEFAULT_TOP_UP_MAX);
                    config.setBonusTopUpMinAmount(DEFAULT_BONUS_TOP_UP_MIN);
                    config.setBonusTopUpMaxAmount(DEFAULT_BONUS_TOP_UP_MAX);
                    config.setMinTickets(DEFAULT_MIN_TICKETS);
                    config.setMaxTickets(DEFAULT_MAX_TICKETS);
                    config.setWithdrawalCommissionPercentage(DEFAULT_WITHDRAWAL_COMMISSION);
                    config.setReferralCommissionPercentage(DEFAULT_REFERRAL_COMMISSION);
                    config.setTicketCalculationAmount(DEFAULT_TICKET_CALCULATION);
                    config.setDailyBonusTransferLimit(DEFAULT_DAILY_BONUS_TRANSFER_LIMIT);
                    config.setTopUpDailyLimitIncreasePercentage(DEFAULT_TOP_UP_DAILY_LIMIT_INCREASE_PERCENTAGE);
                    config.setDepositDailyLimitIncreasePercentage(DEFAULT_DEPOSIT_DAILY_LIMIT_INCREASE_PERCENTAGE);
                    config.setHumoEnabled(DEFAULT_HUMO_ENABLED);
                    config.setUzcardRail(DEFAULT_UZCARD_RAIL);
                    config.setHumoLegacyDualCheckEnd(null);
                    config.setLotteryCooldownSeconds(DEFAULT_LOTTERY_COOLDOWN_SECONDS);
                    config.setWalletMinWithdrawAmount(DEFAULT_WALLET_MIN_WITHDRAW_AMOUNT);
                    config.setWalletTransferMinAmount(DEFAULT_WALLET_TRANSFER_MIN);
                    config.setWalletTransferMaxAmount(DEFAULT_WALLET_TRANSFER_MAX);
                    config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    return configurationRepository.save(config);
                });
    }

    @Transactional
    public SystemConfiguration updateConfiguration(SystemConfiguration config) {
        if (config.getHumoEnabled() == null) {
            config.setHumoEnabled(
                    configurationRepository
                            .findFirstByOrderByCreatedAtDesc()
                            .map(c -> c.getHumoEnabled() != null ? c.getHumoEnabled() : DEFAULT_HUMO_ENABLED)
                            .orElse(DEFAULT_HUMO_ENABLED));
        }
        if (config.getUzcardRail() == null) {
            config.setUzcardRail(DEFAULT_UZCARD_RAIL);
        }
        config.setHumoLegacyDualCheckEnd(null);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        SystemConfiguration saved = configurationRepository.save(config);
        logger.info("System configuration updated: {}", saved.getId());
        return saved;
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

    public BigDecimal getWithdrawalCommissionPercentage() {
        SystemConfiguration config = getConfiguration();
        return config.getWithdrawalCommissionPercentage() != null
                ? config.getWithdrawalCommissionPercentage()
                : DEFAULT_WITHDRAWAL_COMMISSION;
    }

    public BigDecimal getReferralCommissionPercentage() {
        SystemConfiguration config = getConfiguration();
        return config.getReferralCommissionPercentage() != null
                ? config.getReferralCommissionPercentage()
                : DEFAULT_REFERRAL_COMMISSION;
    }

    public Long getTicketCalculationAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getTicketCalculationAmount() != null
                ? config.getTicketCalculationAmount()
                : DEFAULT_TICKET_CALCULATION;
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

    public Long getWalletMinWithdrawAmount() {
        SystemConfiguration config = getConfiguration();
        return config.getWalletMinWithdrawAmount() != null
                ? config.getWalletMinWithdrawAmount()
                : DEFAULT_WALLET_MIN_WITHDRAW_AMOUNT;
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
    public void setHumoEnabled(boolean enabled) {
        SystemConfiguration current = getConfiguration();
        SystemConfiguration config = copyConfig(current);
        config.setHumoEnabled(enabled);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        configurationRepository.save(config);
        logger.info("HUMO enabled set to {}", enabled);
    }

    /**
     * Sets global UZ top-up mode (Oson / CardXabar / off). New config row for history.
     */
    @Transactional
    public void setUzcardRail(UzcardRail rail) {
        if (rail == null) {
            rail = DEFAULT_UZCARD_RAIL;
        }
        SystemConfiguration current = getConfiguration();
        SystemConfiguration config = copyConfig(current);
        config.setUzcardRail(rail);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        configurationRepository.save(config);
        logger.info("UZCARD rail set to {}", rail);
    }

    /**
     * Updates only the wallet withdraw ratio by creating a new config row (history preserved).
     * Does not modify the managed entity, so no Hibernate "identifier altered" error.
     */
    @Transactional
    public SystemConfiguration setWalletWithdrawRatio(Long ratio) {
        SystemConfiguration current = getConfiguration();
        SystemConfiguration config = copyConfig(current);
        config.setWalletWithdrawRatio(ratio);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        SystemConfiguration saved = configurationRepository.save(config);
        logger.info("Wallet withdraw ratio updated to {}", ratio);
        return saved;
    }

    /**
     * Updates only the wallet minimum withdraw amount by creating a new config row (history preserved).
     * Does not modify the managed entity, so no Hibernate "identifier altered" error.
     */
    @Transactional
    public SystemConfiguration setWalletMinWithdrawAmount(Long amount) {
        SystemConfiguration current = getConfiguration();
        SystemConfiguration config = copyConfig(current);
        config.setWalletMinWithdrawAmount(amount);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        SystemConfiguration saved = configurationRepository.save(config);
        logger.info("Wallet min withdraw amount updated to {}", amount);
        return saved;
    }

    @Transactional
    public SystemConfiguration setWalletTransferMinAmount(Long amount) {
        SystemConfiguration current = getConfiguration();
        SystemConfiguration config = copyConfig(current);
        config.setWalletTransferMinAmount(amount);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        SystemConfiguration saved = configurationRepository.save(config);
        logger.info("Wallet transfer min amount updated to {}", amount);
        return saved;
    }

    @Transactional
    public SystemConfiguration setWalletTransferMaxAmount(Long amount) {
        SystemConfiguration current = getConfiguration();
        SystemConfiguration config = copyConfig(current);
        config.setWalletTransferMaxAmount(amount);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        SystemConfiguration saved = configurationRepository.save(config);
        logger.info("Wallet transfer max amount updated to {}", amount);
        return saved;
    }

    private SystemConfiguration copyConfig(SystemConfiguration current) {
        SystemConfiguration config = new SystemConfiguration();
        config.setTopUpMinAmount(current.getTopUpMinAmount());
        config.setTopUpMaxAmount(current.getTopUpMaxAmount());
        config.setBonusTopUpMinAmount(current.getBonusTopUpMinAmount());
        config.setBonusTopUpMaxAmount(current.getBonusTopUpMaxAmount());
        config.setMinTickets(current.getMinTickets());
        config.setMaxTickets(current.getMaxTickets());
        config.setWithdrawalCommissionPercentage(current.getWithdrawalCommissionPercentage());
        config.setReferralCommissionPercentage(current.getReferralCommissionPercentage());
        config.setTicketCalculationAmount(current.getTicketCalculationAmount());
        config.setDailyBonusTransferLimit(current.getDailyBonusTransferLimit());
        config.setTopUpDailyLimitIncreasePercentage(current.getTopUpDailyLimitIncreasePercentage());
        config.setDepositDailyLimitIncreasePercentage(current.getDepositDailyLimitIncreasePercentage());
        config.setHumoEnabled(
                current.getHumoEnabled() != null ? current.getHumoEnabled() : DEFAULT_HUMO_ENABLED);
        config.setUzcardRail(current.getUzcardRail() != null ? current.getUzcardRail() : DEFAULT_UZCARD_RAIL);
        config.setHumoLegacyDualCheckEnd(null);
        config.setLotteryCooldownSeconds(current.getLotteryCooldownSeconds());
        config.setWalletMinWithdrawAmount(current.getWalletMinWithdrawAmount());
        config.setWalletWithdrawRatio(current.getWalletWithdrawRatio());
        config.setWalletTransferMinAmount(current.getWalletTransferMinAmount());
        config.setWalletTransferMaxAmount(current.getWalletTransferMaxAmount());
        return config;
    }
}
