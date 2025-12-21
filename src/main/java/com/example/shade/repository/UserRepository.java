package com.example.shade.repository;

import com.example.shade.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByChatId(Long chatId);

    @org.springframework.data.jpa.repository.Query("SELECT new com.example.shade.dto.UserStatusDTO(u.chatId, CAST(u.language AS string), CASE WHEN b.chatId IS NOT NULL THEN true ELSE false END, b.phoneNumber) "
            +
            "FROM User u LEFT JOIN BlockedUser b ON u.chatId = b.chatId " +
            "ORDER BY CASE WHEN b.chatId IS NOT NULL THEN 1 ELSE 0 END DESC, u.chatId ASC")
    org.springframework.data.domain.Page<com.example.shade.dto.UserStatusDTO> findAllWithBlockedStatus(
            org.springframework.data.domain.Pageable pageable);
}