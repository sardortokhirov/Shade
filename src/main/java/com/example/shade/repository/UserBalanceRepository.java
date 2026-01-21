package com.example.shade.repository;

import com.example.shade.model.UserBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserBalanceRepository extends JpaRepository<UserBalance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ub FROM UserBalance ub WHERE ub.chatId = :chatId")
    Optional<UserBalance> findByIdWithLock(@Param("chatId") Long chatId);
}