package com.example.shade.service;

import com.example.shade.model.SystemConfiguration;
import com.example.shade.repository.SystemConfigurationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Transactional
    public SystemConfiguration getConfiguration() {
        return configurationRepository.findLatest()
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
                    config.setCreatedAt(LocalDateTime.now());
                    return configurationRepository.save(config);
                });
    }

    @Transactional
    public SystemConfiguration updateConfiguration(SystemConfiguration config) {
        config.setCreatedAt(LocalDateTime.now());
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
}
