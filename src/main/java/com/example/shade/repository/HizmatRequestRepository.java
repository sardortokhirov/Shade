package com.example.shade.repository;

import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Date-6/14/2025
 * By Sardor Tokhirov
 * Time-7:56 AM (GMT+5)
 */
@Repository
public interface HizmatRequestRepository extends JpaRepository<HizmatRequest, Long>, JpaSpecificationExecutor<HizmatRequest> {
    @Query("SELECT r FROM HizmatRequest r WHERE " +
            "(:cardId IS NULL OR r.adminCardId = :cardId) AND " +
            "(:platformId IS NULL OR r.platform IN (SELECT p.name FROM Platform p WHERE p.id = :platformId)) AND " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:type IS NULL OR r.type = :type) " +
            "ORDER BY r.createdAt DESC")
    List<HizmatRequest> findByFilters(
            @Param("cardId") Long cardId,
            @Param("platformId") Long platformId,
            @Param("status") RequestStatus status,
            @Param("type") RequestType type);

    @Query("""
                SELECT r FROM HizmatRequest r
                WHERE (:status IS NULL OR r.status = :status)
                ORDER BY r.createdAt DESC
            """)
    List<HizmatRequest> findByFilters(
            @Param("status") RequestStatus status,
            Pageable pageable);


    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId AND h.platform = :platform ORDER BY h.createdAt DESC")
    List<HizmatRequest> findTop3ByChatIdAndPlatformOrderByCreatedAtDesc(Long chatId, String platform);

    @Query("""
                SELECT h FROM HizmatRequest h
                WHERE h.cardNumber IS NOT NULL
                  AND h.chatId = :chatId
                  AND h.createdAt = (
                    SELECT MAX(h2.createdAt) FROM HizmatRequest h2
                    WHERE h2.cardNumber = h.cardNumber AND h2.chatId = h.chatId
                  )
                ORDER BY h.createdAt DESC
            """)
    List<HizmatRequest> findLatestUniqueCardNumbersByChatId(@Param("chatId") Long chatId);

    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId AND h.status = :status ORDER BY h.createdAt DESC limit 1")
    Optional<HizmatRequest> findByChatIdAndStatus(Long chatId, RequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId AND h.status = :status ORDER BY h.createdAt DESC limit 1")
    Optional<HizmatRequest> findByChatIdAndStatusForUpdate(Long chatId, RequestStatus status);

    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId AND h.status = :status ")
    List<HizmatRequest> findByChatsIdAndStatus(Long chatId, RequestStatus status);

    @Query("SELECT h FROM HizmatRequest h WHERE h.status = :status")
    List<HizmatRequest> findByStatus(RequestStatus status);

    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId AND h.platform = :platform AND h.platformUserId = :platformUserId ORDER BY h.createdAt DESC LIMIT 1")
    Optional<HizmatRequest> findTopByChatIdAndPlatformAndPlatformUserIdOrderByCreatedAtDesc(Long chatId, String platform, String platformUserId);

    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId AND h.platform = :platform AND h.platformUserId = :platformUserId AND h.status = :status ORDER BY h.createdAt DESC LIMIT 1")
    Optional<HizmatRequest> findTopByChatIdAndPlatformAndPlatformUserIdAndStatusOrderByCreatedAtDesc(
            @Param("chatId") Long chatId,
            @Param("platform") String platform,
            @Param("platformUserId") String platformUserId,
            @Param("status") RequestStatus status);

    @Query("SELECT DISTINCT h.chatId FROM HizmatRequest h WHERE h.status = 'APPROVED'")
    List<Long> findDistinctChatIdsByStatusApproved();

    @Query("SELECT MIN(h.createdAt) FROM HizmatRequest h WHERE h.chatId = :chatId")
    Optional<LocalDateTime> findEarliestByChatId(@Param("chatId") Long chatId);

    @Query("SELECT DISTINCT h.platform FROM HizmatRequest h WHERE h.chatId = :chatId")
    List<String> findDistinctPlatformsByChatId(@Param("chatId") Long chatId);

    @Query("SELECT h FROM HizmatRequest h WHERE h.chatId = :chatId " +
            "AND (:status IS NULL OR h.status = :status) " +
            "AND (:platform IS NULL OR h.platform = :platform) " +
            "AND (:type IS NULL OR h.type = :type) " +
            "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR h.createdAt <= :endDate) " +
            "ORDER BY h.createdAt DESC")
    org.springframework.data.domain.Page<HizmatRequest> findByChatIdAndFilters(
            @Param("chatId") Long chatId,
            @Param("status") RequestStatus status,
            @Param("platform") String platform,
            @Param("type") RequestType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(h.amount), 0) FROM HizmatRequest h WHERE h.chatId = :chatId AND h.type = 'TOP_UP' AND h.status = 'APPROVED'")
    Long sumTopUpAmountByChatId(@Param("chatId") Long chatId);

    @Query("SELECT COALESCE(SUM(h.amount), 0) FROM HizmatRequest h WHERE h.chatId = :chatId AND h.type = 'WITHDRAWAL' AND h.status = 'APPROVED'")
    Long sumTransferAmountByChatId(@Param("chatId") Long chatId);

    @Query("SELECT COUNT(h) FROM HizmatRequest h WHERE h.chatId = :chatId AND h.status = :status")
    Long countByChatIdAndStatus(@Param("chatId") Long chatId, @Param("status") RequestStatus status);

    @Query("SELECT COUNT(h) FROM HizmatRequest h WHERE h.chatId = :chatId")
    Long countByChatId(@Param("chatId") Long chatId);

    @Query("SELECT MIN(h.createdAt) FROM HizmatRequest h WHERE h.chatId = :chatId")
    Optional<LocalDateTime> findFirstRequestDateByChatId(@Param("chatId") Long chatId);

    @Query("SELECT MAX(h.createdAt) FROM HizmatRequest h WHERE h.chatId = :chatId")
    Optional<LocalDateTime> findLastRequestDateByChatId(@Param("chatId") Long chatId);
}