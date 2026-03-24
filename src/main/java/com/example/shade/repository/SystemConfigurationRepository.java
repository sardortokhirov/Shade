package com.example.shade.repository;

import com.example.shade.model.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {

    Optional<SystemConfiguration> findFirstByOrderByCreatedAtDesc();
}
