package com.example.shade.model;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Date-8/11/2025
 * By Sardor Tokhirov
 * Time-4:28 AM (GMT+5)
 */

@Entity
@Table(name = "feature_settings")
@Data
public class FeatureSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "top_up_enabled", nullable = false)
    private Boolean topUpEnabled = true;

    @Column(name = "withdraw_enabled", nullable = false)
    private Boolean withdrawEnabled = true;

    @Column(name = "bonus_enabled", nullable = false)
    private Boolean bonusEnabled = true;

    /** Wallet (hamyon) feature toggle. Nullable so Hibernate can add the column to existing rows without a NOT NULL failure. */
    @Column(name = "wallet_enabled")
    private Boolean walletEnabled = true;

    @Column(name = "promo_enabled")
    private Boolean promoEnabled = false;

    @Column(name = "bonus_limit_enabled")
    private Boolean bonusLimitEnabled = true;

    @Column(name = "pay_toggle_enabled")
    private Boolean payToggleEnabled = false;

    /** Null treated as disabled. */
    @Column(name = "bonus_auto_approve_enabled")
    private Boolean bonusAutoApproveEnabled = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Not persisted; filled from {@code SystemConfiguration.humoEnabled} for admin GET /features. */
    @Transient
    private Boolean humoEnabled;
}