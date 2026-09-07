package com.example.shade.repository;

import com.example.shade.model.LotteryConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Repository for LotteryConfiguration entity
 */
public interface LotteryConfigurationRepository extends JpaRepository<LotteryConfiguration, Long> {
    @Query("SELECT lc FROM LotteryConfiguration lc ORDER BY lc.createdAt DESC LIMIT 1")
    Optional<LotteryConfiguration> findLatest();
}
