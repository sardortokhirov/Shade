package com.example.shade.repository;

import com.example.shade.model.UserWalletQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWalletQuotaRepository extends JpaRepository<UserWalletQuota, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM UserWalletQuota q WHERE q.chatId = :chatId")
    Optional<UserWalletQuota> findByIdWithLock(@Param("chatId") Long chatId);
}
