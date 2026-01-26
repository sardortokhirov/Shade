package com.example.shade.repository;

import com.example.shade.model.LotteryPrize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotteryPrizeRepository extends JpaRepository<LotteryPrize, Long> {

    @Modifying
    @Query("UPDATE LotteryPrize p SET p.numberOfPrize = p.numberOfPrize - :decrement WHERE p.id = :id")
    void decrementNumberOfPrize(@Param("id") Long id, @Param("decrement") int decrement);
}