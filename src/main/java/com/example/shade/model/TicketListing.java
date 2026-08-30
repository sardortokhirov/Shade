package com.example.shade.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_listing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketListing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_chat_id", nullable = false)
    private Long sellerChatId;

    @Column(name = "ticket_quantity", nullable = false)
    private Long ticketQuantity;

    /** Total listing price in UZS (buyer pays this in full). */
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketListingStatus status;

    @Column(name = "buyer_chat_id")
    private Long buyerChatId;

    @Column(name = "fee_amount")
    private Long feeAmount;

    @Column(name = "net_amount")
    private Long netAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;
}
