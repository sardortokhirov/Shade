package com.example.shade.repository;

import com.example.shade.model.BotTipConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BotTipConfigurationRepository extends JpaRepository<BotTipConfiguration, Long> {
    @Query("SELECT c FROM BotTipConfiguration c ORDER BY c.createdAt DESC limit 1")
    Optional<BotTipConfiguration> findLatest();
}
