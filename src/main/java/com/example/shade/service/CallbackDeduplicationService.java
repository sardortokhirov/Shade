package com.example.shade.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents Telegram callback double-taps from executing the same action twice.
 */
@Service
public class CallbackDeduplicationService {
    private static final long EXPIRY_TIME_MS = 60_000L;

    private final ConcurrentHashMap<String, Long> processedCallbacks = new ConcurrentHashMap<>();

    public boolean tryProcess(String callbackId) {
        if (callbackId == null || callbackId.isBlank()) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long existing = processedCallbacks.putIfAbsent(callbackId, now);
        if (existing == null) {
            return true;
        }
        if (now - existing > EXPIRY_TIME_MS) {
            return processedCallbacks.replace(callbackId, existing, now);
        }
        return false;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        processedCallbacks.entrySet().removeIf(entry -> now - entry.getValue() > EXPIRY_TIME_MS);
    }
}
