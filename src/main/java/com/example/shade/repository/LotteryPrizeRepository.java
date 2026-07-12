package com.example.shade.repository;

import com.example.shade.model.LotteryPrize;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LotteryPrizeRepository extends JpaRepository<LotteryPrize, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LotteryPrize p")
    List<LotteryPrize> findAllWithLock();
}