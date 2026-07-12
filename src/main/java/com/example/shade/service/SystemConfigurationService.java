package com.example.shade.service;

import com.example.shade.model.SystemConfiguration;
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
                    config.setReferralCommissionPercentage(DEFAULT_REFERRAL_COMMISSION);
                    config.setWithdrawFeePercentage(DEFAULT_WITHDRAW_FEE_PERCENTAGE);
                    config.setTicketCalculationAmount(DEFAULT_TICKET_CALCULATION);
                    config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    return configurationRepository.save(config);
                });
    }

    @Transactional
    public SystemConfiguration updateConfiguration(SystemConfiguration config) {
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
        return configurationRepository.save(config);
    }

    @Transactional
    public SystemConfiguration setWalletMinWithdrawAmount(Long amount) {
        SystemConfiguration config = getConfiguration();
        config.setWalletMinWithdrawAmount(amount);
        return configurationRepository.save(config);
    }

    @Transactional
    public SystemConfiguration setWalletTransferMinAmount(Long amount) {
        SystemConfiguration config = getConfiguration();
        config.setWalletTransferMinAmount(amount);
        return configurationRepository.save(config);
    }

    @Transactional
    public SystemConfiguration setWalletTransferMaxAmount(Long amount) {
        SystemConfiguration config = getConfiguration();
        config.setWalletTransferMaxAmount(amount);
        return configurationRepository.save(config);
    }
}
