package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of validating a platform user ID for wallet transfer.
 * If valid, fullName is set; if invalid, errorMessageKey is set for i18n.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletUserIdValidationResult {
    private boolean valid;
    private String fullName;
    private String errorMessageKey;

    public static WalletUserIdValidationResult valid(String fullName) {
        return new WalletUserIdValidationResult(true, fullName != null ? fullName : "", null);
    }

    public static WalletUserIdValidationResult invalid(String errorMessageKey) {
        return new WalletUserIdValidationResult(false, null, errorMessageKey);
    }
}
