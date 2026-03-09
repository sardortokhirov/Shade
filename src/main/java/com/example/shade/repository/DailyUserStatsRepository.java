package com.example.shade.repository;

import com.example.shade.model.DailyUserStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for DailyUserStats entity
 */
public interface DailyUserStatsRepository extends JpaRepository<DailyUserStats, Long> {
    Optional<DailyUserStats> findByChatIdAndDate(Long chatId, LocalDate date);

    Page<DailyUserStats> findByChatIdAndDateBetween(Long chatId, LocalDate startDate, LocalDate endDate,
            Pageable pageable);

    Page<DailyUserStats> findByChatIdOrderByDateDesc(Long chatId, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @jakarta.transaction.Transactional
    void deleteByChatId(Long chatId);
}
