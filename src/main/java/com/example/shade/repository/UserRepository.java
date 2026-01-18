package com.example.shade.repository;

import com.example.shade.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
        Optional<User> findByChatId(Long chatId);

        @org.springframework.data.jpa.repository.Query("SELECT new com.example.shade.dto.UserStatusDTO(u.chatId, CAST(u.language AS string), CASE WHEN b.phoneNumber = 'BLOCKED' THEN true ELSE false END, b.phoneNumber) "
                        +
                        "FROM User u LEFT JOIN BlockedUser b ON u.chatId = b.chatId " +
                        "ORDER BY CASE WHEN b.phoneNumber = 'BLOCKED' THEN 1 ELSE 0 END DESC, u.chatId ASC")
        org.springframework.data.domain.Page<com.example.shade.dto.UserStatusDTO> findAllWithBlockedStatus(
                        org.springframework.data.domain.Pageable pageable);
        
        /**
         * Find chatIds from User table that match the search pattern (partial match)
         * Uses CAST to convert chatId to string for LIKE comparison
         */
        @Query("SELECT u.chatId FROM User u WHERE CAST(u.chatId AS string) LIKE CONCAT('%', :searchPattern, '%')")
        List<Long> findChatIdsBySearchPattern(@Param("searchPattern") String searchPattern);
        
        /**
         * Find chatIds from User table filtered by language
         */
        @Query("SELECT u.chatId FROM User u WHERE u.language = :language")
        List<Long> findChatIdsByLanguage(@Param("language") com.example.shade.model.Language language);
        
        /**
         * Find chatIds from User table filtered by language and search pattern
         */
        @Query("SELECT u.chatId FROM User u WHERE CAST(u.chatId AS string) LIKE CONCAT('%', :searchPattern, '%') AND u.language = :language")
        List<Long> findChatIdsBySearchPatternAndLanguage(@Param("searchPattern") String searchPattern, @Param("language") com.example.shade.model.Language language);
}