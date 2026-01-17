package com.example.shade.repository;

import com.example.shade.model.LotteryTicketBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LotteryTicketBundleRepository extends JpaRepository<LotteryTicketBundle, Long> {
    List<LotteryTicketBundle> findByIsActiveTrueOrderByDisplayOrderAsc();
}
