package com.example.shade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to prevent duplicate processing of Telegram callback queries.
 * When users click buttons multiple times (especially with slow internet),
 * this service ensures only the first click is processed.
 */
@Service
public class CallbackDeduplicationService {
    private static final Logger logger = LoggerFactory.getLogger(CallbackDeduplicationService.class);
    
    // Time in milliseconds after which a callback ID expires (60 seconds)
    private static final long EXPIRY_TIME_MS = 60_000L;
    
    // Thread-safe map to store callback IDs with their processing timestamp
    private final ConcurrentHashMap<String, Long> processedCallbacks = new ConcurrentHashMap<>();
    
    /**
     * Attempts to mark a callback as processed.
     * 
     * @param callbackId The unique callback query ID from Telegram
     * @return true if this is a new callback (should be processed), 
     *         false if it's a duplicate (should be skipped)
     */
    public boolean tryProcess(String callbackId) {
        if (callbackId == null || callbackId.isEmpty()) {
            return true; // Allow processing if no valid ID
        }
        
        long currentTime = System.currentTimeMillis();
        
        // putIfAbsent returns null if the key was not present (new callback)
        // returns the existing value if key was already present (duplicate)
        Long existingTimestamp = processedCallbacks.putIfAbsent(callbackId, currentTime);
        
        if (existingTimestamp == null) {
            // New callback - allow processing
            logger.debug("New callback registered: {}", callbackId);
            return true;
        } else {
            // Duplicate callback - check if it's expired
            if (currentTime - existingTimestamp > EXPIRY_TIME_MS) {
                // Expired entry, update timestamp and allow processing
                processedCallbacks.put(callbackId, currentTime);
                logger.debug("Expired callback re-registered: {}", callbackId);
                return true;
            }
            // Recent duplicate - skip processing
            logger.debug("Duplicate callback ignored: {} (age: {}ms)", callbackId, currentTime - existingTimestamp);
            return false;
        }
    }
    
    /**
     * Scheduled task to clean up expired callback entries.
     * Runs every 5 minutes to prevent memory buildup.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        Iterator<Map.Entry<String, Long>> iterator = processedCallbacks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > EXPIRY_TIME_MS) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            logger.debug("Cleaned up {} expired callback entries. Remaining: {}", 
                    removedCount, processedCallbacks.size());
        }
    }
    
    /**
     * Gets the current number of tracked callbacks (for monitoring/debugging)
     */
    public int getTrackedCallbackCount() {
        return processedCallbacks.size();
    }
}
