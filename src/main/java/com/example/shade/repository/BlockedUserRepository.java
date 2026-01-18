package com.example.shade.repository;

import com.example.shade.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    boolean existsByChatId(Long chatId);

    Optional<BlockedUser> findByChatId(Long chatId);

    List<BlockedUser> findAllByPhoneNumberNot(String phoneNumber);
    
    /**
     * Find chatIds from BlockedUser table that match the search pattern (partial match)
     */
    @Query("SELECT b.chatId FROM BlockedUser b WHERE CAST(b.chatId AS string) LIKE CONCAT('%', :searchPattern, '%')")
    List<Long> findChatIdsBySearchPattern(@Param("searchPattern") String searchPattern);
}