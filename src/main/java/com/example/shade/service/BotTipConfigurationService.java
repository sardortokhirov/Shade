package com.example.shade.service;

import com.example.shade.dto.BotTipConfigStatusDTO;
import com.example.shade.dto.BotTipStatsDTO;
import com.example.shade.model.BotTipConfiguration;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.repository.BotTipConfigurationRepository;
import com.example.shade.repository.HizmatRequestRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BotTipConfigurationService {

    private final BotTipConfigurationRepository repository;
    private final HizmatRequestRepository requestRepository;

    private static final String DEFAULT_PRESETS = "5000,10000,20000";
    private static final Long DEFAULT_MIN_AMOUNT = 5000L;
    private static final Long DEFAULT_MIN_BONUS_TICKETS = 0L;
    private static final Long DEFAULT_MAX_BONUS_TICKETS = 0L;
    private static final Boolean DEFAULT_BONUS_TICKETS_ENABLED = true;
    private static final Integer DEFAULT_BONUS_TICKETS_CHANCE = 100;

    @Transactional
    public BotTipConfiguration getConfiguration() {
        return repository.findLatest()
                .orElseGet(() -> {
                    BotTipConfiguration config = new BotTipConfiguration();
                    config.setPresets(DEFAULT_PRESETS);
                    config.setMinAmount(DEFAULT_MIN_AMOUNT);
                    config.setMinBonusTickets(DEFAULT_MIN_BONUS_TICKETS);
                    config.setMaxBonusTickets(DEFAULT_MAX_BONUS_TICKETS);
                    config.setBonusTicketsEnabled(DEFAULT_BONUS_TICKETS_ENABLED);
                    config.setBonusTicketsChance(DEFAULT_BONUS_TICKETS_CHANCE);
                    config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    return repository.save(config);
                });
    }

    @Transactional
    public BotTipConfiguration updateConfiguration(BotTipConfiguration config) {
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        BotTipConfiguration existing = config.getId() != null ? repository.findById(config.getId()).orElse(null) : repository.findLatest().orElse(null);
        if (existing != null) {
            if (config.getBonusTicketsEnabled() == null) config.setBonusTicketsEnabled(existing.getBonusTicketsEnabled());
            if (config.getBonusTicketsChance() == null) config.setBonusTicketsChance(existing.getBonusTicketsChance());
            if (config.getMinBonusTickets() == null) config.setMinBonusTickets(existing.getMinBonusTickets());
            if (config.getMaxBonusTickets() == null) config.setMaxBonusTickets(existing.getMaxBonusTickets());
        } else {
            if (config.getBonusTicketsEnabled() == null) config.setBonusTicketsEnabled(DEFAULT_BONUS_TICKETS_ENABLED);
            if (config.getBonusTicketsChance() == null) config.setBonusTicketsChance(DEFAULT_BONUS_TICKETS_CHANCE);
            if (config.getMinBonusTickets() == null) config.setMinBonusTickets(DEFAULT_MIN_BONUS_TICKETS);
            if (config.getMaxBonusTickets() == null) config.setMaxBonusTickets(DEFAULT_MAX_BONUS_TICKETS);
        }
        return repository.save(config);
    }

    public String getPresets() {
        BotTipConfiguration config = getConfiguration();
        return config.getPresets() != null ? config.getPresets() : DEFAULT_PRESETS;
    }

    public Long getMinAmount() {
        BotTipConfiguration config = getConfiguration();
        return config.getMinAmount() != null ? config.getMinAmount() : DEFAULT_MIN_AMOUNT;
    }

    /**
     * Returns a random number of bonus tickets within the configured range (inclusive).
     * Returns 0 if: disabled, range invalid, or chance roll fails.
     * Uses bonusTicketsEnabled and bonusTicketsChance (0-100) for dynamic control.
     */
    public long getRandomBonusTickets() {
        BotTipConfiguration config = getConfiguration();
        Boolean enabled = config.getBonusTicketsEnabled() != null ? config.getBonusTicketsEnabled() : DEFAULT_BONUS_TICKETS_ENABLED;
        if (!Boolean.TRUE.equals(enabled)) {
            return 0;
        }
        Long min = config.getMinBonusTickets() != null ? config.getMinBonusTickets() : DEFAULT_MIN_BONUS_TICKETS;
        Long max = config.getMaxBonusTickets() != null ? config.getMaxBonusTickets() : DEFAULT_MAX_BONUS_TICKETS;
        if (min == null || max == null || min < 0 || max < 0 || min > max) {
            return 0;
        }
        if (min == 0 && max == 0) {
            return 0;
        }
        Integer chance = config.getBonusTicketsChance() != null ? config.getBonusTicketsChance() : DEFAULT_BONUS_TICKETS_CHANCE;
        int chancePercent = Math.max(0, Math.min(100, chance));
        if (chancePercent < 100 && ThreadLocalRandom.current().nextInt(100) >= chancePercent) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    /**
     * Returns frontend-friendly status for the tip bonus feature.
     */
    public BotTipConfigStatusDTO getBonusStatus() {
        BotTipConfiguration config = getConfiguration();
        boolean enabled = Boolean.TRUE.equals(config.getBonusTicketsEnabled());
        Long min = config.getMinBonusTickets() != null ? config.getMinBonusTickets() : 0L;
        Long max = config.getMaxBonusTickets() != null ? config.getMaxBonusTickets() : 0L;
        int chance = config.getBonusTicketsChance() != null ? Math.max(0, Math.min(100, config.getBonusTicketsChance())) : 100;

        boolean effectivelyEnabled = enabled && min != null && max != null && min >= 0 && max >= min;
        String statusCode = effectivelyEnabled ? "enabled" : "disabled";
        String bonusRangeSummary;
        String statusDescription;
        if (!enabled) {
            bonusRangeSummary = "Disabled";
            statusDescription = "Tip bonus tickets are disabled.";
        } else if (!effectivelyEnabled) {
            bonusRangeSummary = "Invalid range";
            statusDescription = "Tip bonus is enabled but min/max range is invalid (min=" + min + ", max=" + max + ").";
        } else {
            bonusRangeSummary = min + "-" + max + " tickets, " + chance + "% chance";
            statusDescription = "Tip bonus: " + min + "-" + max + " random tickets per tip, " + chance + "% chance";
        }

        return BotTipConfigStatusDTO.builder()
                .bonusTicketsEnabled(enabled)
                .minBonusTickets(min != null ? min : 0)
                .maxBonusTickets(max != null ? max : 0)
                .bonusTicketsChance(chance)
                .statusCode(statusCode)
                .statusDescription(statusDescription)
                .bonusRangeSummary(bonusRangeSummary)
                .build();
    }

    public BotTipStatsDTO getTipStats(LocalDateTime startDate, LocalDateTime endDate) {
        List<HizmatRequest> tipRequests = requestRepository.findByFilters(
                null, null, RequestStatus.APPROVED, RequestType.TIP);
        if (startDate != null) {
            tipRequests = tipRequests.stream().filter(r -> !r.getCreatedAt().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            tipRequests = tipRequests.stream().filter(r -> !r.getCreatedAt().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        long count = tipRequests.size();
        double amount = tipRequests.stream().mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                .sum();

        Map<String, Long> countByDate = tipRequests.stream().collect(Collectors.groupingBy(
                r -> r.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                Collectors.counting()));

        Map<String, Double> amountByDate = tipRequests.stream().collect(Collectors.groupingBy(
                r -> r.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                Collectors.summingDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)));

        return new BotTipStatsDTO(count, amount, countByDate, amountByDate);
    }

    public List<HizmatRequest> getTipTransactions(LocalDateTime startDate, LocalDateTime endDate) {
        List<HizmatRequest> tipRequests = requestRepository.findByFilters(
                null, null, null, RequestType.TIP);
        if (startDate != null) {
            tipRequests = tipRequests.stream().filter(r -> !r.getCreatedAt().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            tipRequests = tipRequests.stream().filter(r -> !r.getCreatedAt().isAfter(endDate))
                    .collect(Collectors.toList());
        }
        return tipRequests.stream().limit(200).collect(Collectors.toList());
    }
}
