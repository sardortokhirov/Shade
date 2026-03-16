package com.example.shade.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_payment_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingPaymentMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message_id", nullable = false)
    private Integer messageId;

    @Column(name = "original_text", nullable = false, length = 4000)
    private String originalText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "blurred", nullable = false)
    private boolean blurred;
}

