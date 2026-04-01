package com.example.shade.repository;

import com.example.shade.model.PendingPaymentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PendingPaymentMessageRepository extends JpaRepository<PendingPaymentMessage, Long> {
    List<PendingPaymentMessage> findByBlurredFalseAndCreatedAtBefore(LocalDateTime before);

    boolean existsByChatIdAndHizmatRequestIdAndBlurredTrue(Long chatId, Long hizmatRequestId);
}

