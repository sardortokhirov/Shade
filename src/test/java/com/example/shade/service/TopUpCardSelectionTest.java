package com.example.shade.service;

import com.example.shade.model.AdminCard;
import com.example.shade.model.PaymentSystem;
import com.example.shade.model.UzcardRail;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class TopUpCardSelectionTest {

    @Test
    void enabledHumoIsNotStarvedByOlderUzcardRows() {
        LocalDateTime now = LocalDateTime.now();
        AdminCard oldUzcard = card(1L, PaymentSystem.UZCARD, now.minusHours(2), UzcardRail.OSON);
        AdminCard recentUzcard = card(2L, PaymentSystem.UZCARD, now.minusMinutes(1), UzcardRail.OSON);
        AdminCard humo = card(3L, PaymentSystem.HUMO, now.minusMinutes(10), null);

        AdminCard selected = TopUpService
                .selectFairTopUpAdminCard(
                        List.of(oldUzcard, recentUzcard, humo), true, UzcardRail.OSON)
                .orElseThrow();

        assertSame(humo, selected);
    }

    @Test
    void disabledHumoSelectsLeastRecentlyUsedUzcard() {
        LocalDateTime now = LocalDateTime.now();
        AdminCard oldUzcard = card(1L, PaymentSystem.UZCARD, now.minusHours(2), UzcardRail.OSON);
        AdminCard recentUzcard = card(2L, PaymentSystem.UZCARD, now.minusMinutes(1), UzcardRail.OSON);
        AdminCard humo = card(3L, PaymentSystem.HUMO, null, null);

        AdminCard selected = TopUpService
                .selectFairTopUpAdminCard(
                        List.of(oldUzcard, recentUzcard, humo), false, UzcardRail.OSON)
                .orElseThrow();

        assertSame(oldUzcard, selected);
    }

    @Test
    void uzcardOffSelectsHumo() {
        AdminCard uzcard = card(1L, PaymentSystem.UZCARD, null, UzcardRail.OSON);
        AdminCard humo = card(2L, PaymentSystem.HUMO, null, null);

        AdminCard selected = TopUpService
                .selectFairTopUpAdminCard(List.of(uzcard, humo), true, UzcardRail.OFF)
                .orElseThrow();

        assertSame(humo, selected);
    }

    private AdminCard card(Long id, PaymentSystem paymentSystem, LocalDateTime lastUsed, UzcardRail rail) {
        AdminCard card = new AdminCard();
        card.setId(id);
        card.setPaymentSystem(paymentSystem);
        card.setLastUsed(lastUsed);
        card.setUzcardRail(rail);
        return card;
    }
}
