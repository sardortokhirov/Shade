package com.example.shade.repository;

import com.example.shade.model.LotteryTicketPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LotteryTicketPurchaseRepository extends JpaRepository<LotteryTicketPurchase, Long> {
    Optional<LotteryTicketPurchase> findByChatId(Long chatId);
}
