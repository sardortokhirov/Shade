package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hizmat_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HizmatRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Column(name = "platform_user_id")
    private String platformUserId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "card_number", length = 16)
    private String cardNumber;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "payment_attempts")
    private Integer paymentAttempts;

    @Column(name = "unique_amount")
    private Long uniqueAmount;

    @Column(name = "admin_card_id")
    private Long adminCardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RequestType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Set when bonus transfer is approved (manual or auto). Used for dashboard date filters. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "pay_url")
    private String payUrl;

    /** User's wallet balance (UZS) at the time this transaction was completed; used for history display. */
    @Column(name = "wallet_balance_at_time")
    private Long walletBalanceAtTime;

    /** Telegram chat ID of the counterparty (wallet P2P receiver or ticket trade seller/buyer). */
    @Column(name = "recipient_chat_id")
    private Long recipientChatId;

    /** Platform fee deducted from the transfer/sale (UZS). Not credited to any user wallet. */
    @Column(name = "fee_amount")
    private Long feeAmount;

    /** Amount credited to the receiver/seller after fee (UZS). */
    @Column(name = "net_amount")
    private Long netAmount;

}