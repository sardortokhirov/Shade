package com.example.shade.repository;

import com.example.shade.model.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Repository for SystemConfiguration entity
 */
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {
    @Query("SELECT sc FROM SystemConfiguration sc ORDER BY sc.createdAt DESC LIMIT 1")
    Optional<SystemConfiguration> findLatest();
}
