package com.example.shade.service;

import com.example.shade.model.AdminCard;
import com.example.shade.model.PaymentSystem;
import com.example.shade.model.UzcardRail;
import com.example.shade.repository.AdminCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Defaults and uniqueness for admin cards (UZCARD rail per row; Humo one PAN per config).
 */
@Service
@RequiredArgsConstructor
public class AdminCardService {

    private final AdminCardRepository adminCardRepository;
    private final SystemConfigurationService configurationService;

    /**
     * Normalize PAN, clear rail for HUMO, default lane for UZCARD when missing.
     */
    public void prepareForSave(AdminCard card) {
        if (card.getCardNumber() != null) {
            card.setCardNumber(card.getCardNumber().replaceAll("\\s+", ""));
        }
        if (card.getPaymentSystem() == PaymentSystem.HUMO) {
            card.setUzcardRail(null);
        } else if (card.getPaymentSystem() == PaymentSystem.UZCARD) {
            if (card.getUzcardRail() == null) {
                UzcardRail g = configurationService.getUzcardRail();
                card.setUzcardRail(g == UzcardRail.OFF ? UzcardRail.OSON : g);
            }
        }
    }

    /**
     * @param excludeId null on insert; exclude this id on update
     */
    public void assertUnique(AdminCard card, Long excludeId) {
        if (card.getOsonConfig() == null || card.getCardNumber() == null || card.getPaymentSystem() == null) {
            return;
        }
        if (card.getPaymentSystem() == PaymentSystem.HUMO) {
            if (adminCardRepository.existsHumoDuplicate(
                    card.getOsonConfig().getId(), card.getCardNumber(), PaymentSystem.HUMO, excludeId)) {
                throw new IllegalStateException(
                        "Bu OsonConfig uchun bu HUMO karta raqami allaqachon mavjud");
            }
            return;
        }
        if (card.getPaymentSystem() == PaymentSystem.UZCARD) {
            UzcardRail rail = card.getUzcardRail();
            if (rail == null) {
                UzcardRail g = configurationService.getUzcardRail();
                rail = g == UzcardRail.OFF ? UzcardRail.OSON : g;
            }
            if (adminCardRepository.existsUzcardDuplicate(
                    card.getOsonConfig().getId(), card.getCardNumber(), PaymentSystem.UZCARD, rail, excludeId)) {
                throw new IllegalStateException(
                        "Bu OsonConfig uchun shu UZCARD raqami va tekshiruv yo'li (Oson/CardXabar) allaqachon mavjud");
            }
        }
    }
}
