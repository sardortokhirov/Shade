package com.example.shade.repository;

import com.example.shade.model.PromoAllowedChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromoAllowedChatRepository extends JpaRepository<PromoAllowedChat, Long> {
    boolean existsByChatId(Long chatId);

    Optional<PromoAllowedChat> findByChatId(Long chatId);

    void deleteByChatId(Long chatId);
}
