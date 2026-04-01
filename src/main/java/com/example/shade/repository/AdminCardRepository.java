package com.example.shade.repository;

import com.example.shade.model.AdminCard;
import com.example.shade.model.OsonConfig;
import com.example.shade.model.PaymentSystem;
import com.example.shade.model.UzcardRail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminCardRepository extends JpaRepository<AdminCard, Long> {

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AdminCard a WHERE a.osonConfig.id = :osonConfigId AND a.cardNumber = :cardNumber AND a.paymentSystem = :ps AND a.uzcardRail = :rail AND (:excludeId IS NULL OR a.id <> :excludeId)")
    boolean existsUzcardDuplicate(
            @Param("osonConfigId") Long osonConfigId,
            @Param("cardNumber") String cardNumber,
            @Param("ps") PaymentSystem ps,
            @Param("rail") UzcardRail rail,
            @Param("excludeId") Long excludeId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AdminCard a WHERE a.osonConfig.id = :osonConfigId AND a.cardNumber = :cardNumber AND a.paymentSystem = :ps AND (:excludeId IS NULL OR a.id <> :excludeId)")
    boolean existsHumoDuplicate(
            @Param("osonConfigId") Long osonConfigId,
            @Param("cardNumber") String cardNumber,
            @Param("ps") PaymentSystem ps,
            @Param("excludeId") Long excludeId);

    @Query("SELECT a FROM AdminCard a WHERE a.osonConfig.primaryConfig = true AND (a.lastUsed IS NULL OR a.lastUsed = (SELECT MIN(a2.lastUsed) FROM AdminCard a2 WHERE a2.osonConfig.primaryConfig = true)) ORDER BY a.lastUsed DESC LIMIT 1")
    Optional<AdminCard> findLeastRecentlyUsed();

    @Query("SELECT a FROM AdminCard a WHERE a.osonConfig.primaryConfig = true AND a.paymentSystem = :paymentSystem AND (a.lastUsed IS NULL OR a.lastUsed = (SELECT MIN(a2.lastUsed) FROM AdminCard a2 WHERE a2.osonConfig.primaryConfig = true AND a2.paymentSystem = :paymentSystem)) ORDER BY a.lastUsed DESC LIMIT 1")
    Optional<AdminCard> findLeastRecentlyUsedByPaymentSystem(@Param("paymentSystem") PaymentSystem paymentSystem);

    List<AdminCard> findByOsonConfig(OsonConfig osonConfig);

    /** Primary Oson pool cards only (used for top-up rotation filtering). */
    List<AdminCard> findAllByOsonConfigPrimaryConfigTrue();
}