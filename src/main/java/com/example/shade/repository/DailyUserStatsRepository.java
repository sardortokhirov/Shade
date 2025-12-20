package com.example.shade.repository;

import com.example.shade.model.DailyUserStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for DailyUserStats entity
 */
public interface DailyUserStatsRepository extends JpaRepository<DailyUserStats, Long> {
    Optional<DailyUserStats> findByChatIdAndDate(Long chatId, LocalDate date);
}
