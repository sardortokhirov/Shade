package com.example.shade.repository;

import com.example.shade.model.BlockedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    boolean existsByChatId(Long chatId);

    Optional<BlockedUser> findByChatId(Long chatId);

    List<BlockedUser> findAllByPhoneNumberNot(String phoneNumber);

    Page<BlockedUser> findByPhoneNumberNot(String phoneNumber, Pageable pageable);

    long countByPhoneNumberNot(String phoneNumber);

    @Query(value = "SELECT chat_id FROM blocked_user WHERE CAST(chat_id AS TEXT) LIKE :searchPattern", nativeQuery = true)
    List<Long> findChatIdsBySearchPattern(@Param("searchPattern") String searchPattern);

    @Query("SELECT b.chatId FROM BlockedUser b")
    List<Long> findAllChatIds();

    @Query(value = "SELECT chat_id FROM blocked_user WHERE phone_number ILIKE :searchPattern", nativeQuery = true)
    List<Long> findChatIdsByPhonePattern(@Param("searchPattern") String searchPattern);
}