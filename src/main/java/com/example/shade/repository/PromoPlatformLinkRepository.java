package com.example.shade.repository;

import com.example.shade.model.PromoPlatformLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoPlatformLinkRepository extends JpaRepository<PromoPlatformLink, Long> {
    List<PromoPlatformLink> findByChatIdOrderByCreatedAtDesc(Long chatId);

    List<PromoPlatformLink> findByPlatformUserIdOrderByCreatedAtDesc(String platformUserId);

    boolean existsByChatIdAndPlatformUserId(Long chatId, String platformUserId);

    long countByChatId(Long chatId);

    Optional<PromoPlatformLink> findByIdAndChatId(Long id, Long chatId);

    void deleteByChatId(Long chatId);
}
