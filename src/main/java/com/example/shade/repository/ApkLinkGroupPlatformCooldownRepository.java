package com.example.shade.repository;

import com.example.shade.model.ApkLinkGroupPlatformCooldown;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApkLinkGroupPlatformCooldownRepository extends JpaRepository<ApkLinkGroupPlatformCooldown, Long> {
    Optional<ApkLinkGroupPlatformCooldown> findByChatIdAndPlatformId(Long chatId, Long platformId);
}
