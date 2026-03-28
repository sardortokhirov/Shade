package com.example.shade.util;

/**
 * Canonical phone form for blocklist matching (E.164-style with leading +).
 */
public final class PhoneNormalization {

    private PhoneNormalization() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().replaceAll("[\\s-]", "");
        if (s.startsWith("+")) {
            String digits = s.substring(1).replaceAll("\\D", "");
            if (digits.isEmpty()) {
                return null;
            }
            return "+" + digits;
        }
        String digits = s.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return "+" + digits;
    }
}
