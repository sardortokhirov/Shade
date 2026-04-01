package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, length = 16)
    private String cardNumber;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @ManyToOne
    @JoinColumn(name = "oson_config_id", nullable = false)
    private OsonConfig osonConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_system", nullable = false)
    private PaymentSystem paymentSystem;

    /**
     * UZCARD verification path (Oson vs CardXabar). Must be set for {@link PaymentSystem#UZCARD};
     * must be null for {@link PaymentSystem#HUMO}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "uzcard_rail", length = 32)
    private UzcardRail uzcardRail;
}