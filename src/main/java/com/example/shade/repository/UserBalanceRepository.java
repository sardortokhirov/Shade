package com.example.shade.repository;

import com.example.shade.model.UserBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserBalanceRepository extends JpaRepository<UserBalance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserBalance> findByIdWithLock(Long chatId);
}