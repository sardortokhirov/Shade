package com.example.shade.controller;

import com.example.shade.dto.*;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.service.DailyStatsService;
import com.example.shade.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    private final DailyStatsService dailyStatsService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000998905982808".equals(parts[1]);
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Boolean hasBalance,
            @RequestParam(required = false) Long searchChatId,
            @RequestParam(required = false) String searchPhone,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        // Limit page size
        if (size > 100) {
            size = 100;
        }
        
        Pageable pageable = PageRequest.of(page, size);
        UserFilter filter = new UserFilter(blocked, language, hasBalance, searchChatId, searchPhone);
        
        try {
            Page<UserDTO> users = userService.getUsers(pageable, filter);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching users: " + e.getMessage());
        }
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<?> getUserDetails(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            UserDetailDTO userDetails = userService.getUserDetails(chatId);
            return ResponseEntity.ok(userDetails);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching user details: " + e.getMessage());
        }
    }

    @GetMapping("/{chatId}/transfers")
    public ResponseEntity<?> getUserTransfers(
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        // Limit page size
        if (size > 100) {
            size = 100;
        }
        
        Pageable pageable = PageRequest.of(page, size);
        TransferFilter filter = new TransferFilter(status, platform, type, startDate, endDate);
        
        try {
            Page<HizmatRequest> transfers = userService.getUserTransfers(chatId, pageable, filter);
            return ResponseEntity.ok(transfers);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching transfers: " + e.getMessage());
        }
    }

    @PutMapping("/{chatId}/balance")
    public ResponseEntity<?> updateBalance(
            @PathVariable Long chatId,
            @RequestBody UpdateBalanceRequest request,
            HttpServletRequest httpRequest) {
        
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            com.example.shade.model.UserBalance balance = userService.updateBalance(chatId, request.getBalance());
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating balance: " + e.getMessage());
        }
    }

    @PutMapping("/{chatId}/tickets")
    public ResponseEntity<?> updateTickets(
            @PathVariable Long chatId,
            @RequestBody UpdateTicketsRequest request,
            HttpServletRequest httpRequest) {
        
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            com.example.shade.model.UserBalance balance = userService.updateTickets(chatId, request.getTickets());
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating tickets: " + e.getMessage());
        }
    }

    @PutMapping("/{chatId}/limit")
    public ResponseEntity<?> updateLimit(
            @PathVariable Long chatId,
            @RequestBody UpdateLimitRequest request,
            HttpServletRequest httpRequest) {
        
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        // Log all limit update requests for audit trail
        String clientInfo = httpRequest.getRemoteAddr();
        logger.info("LIMIT UPDATE REQUEST - chatId: {}, requested value: {}, client: {}", 
                chatId, request.getPermanentLimitIncrease(), clientInfo);
        
        // Pre-validation: Check if trying to reset to 0
        if (request.getPermanentLimitIncrease() != null && request.getPermanentLimitIncrease() == 0) {
            try {
                // Check current limit before attempting update
                Long currentLimit = userService.getUserDetails(chatId).getPermanentLimitIncrease();
                if (currentLimit != null && currentLimit > 0) {
                    logger.warn("ATTEMPT TO RESET LIMIT VIA API - chatId: {}, current: {}, requested: 0, client: {}", 
                            chatId, currentLimit, clientInfo);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(String.format("Cannot reset permanent limit increase to 0 when current limit is %d. " +
                                    "Permanent limits should never be reset automatically. " +
                                    "If this is intentional, contact system administrator.", currentLimit));
                }
            } catch (Exception e) {
                // If we can't get current limit, let the service handle the validation
                logger.debug("Could not pre-validate limit reset for chatId {}: {}", chatId, e.getMessage());
            }
        }
        
        try {
            com.example.shade.model.UserLimitIncrease limit = userService.updateLimit(chatId, request.getPermanentLimitIncrease());
            logger.info("LIMIT UPDATE SUCCESS - chatId: {}, new value: {}, client: {}", 
                    chatId, limit.getAccumulatedLimitIncrease(), clientInfo);
            return ResponseEntity.ok(limit);
        } catch (IllegalArgumentException e) {
            logger.error("LIMIT UPDATE FAILED (Validation) - chatId: {}, error: {}, client: {}", 
                    chatId, e.getMessage(), clientInfo);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("LIMIT UPDATE FAILED (Error) - chatId: {}, error: {}, client: {}", 
                    chatId, e.getMessage(), clientInfo);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating limit: " + e.getMessage());
        }
    }

    @PutMapping("/{chatId}/daily-limit")
    public ResponseEntity<?> updateDailyLimit(
            @PathVariable Long chatId,
            @RequestBody UpdateLimitRequest request,
            HttpServletRequest httpRequest) {

        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String clientInfo = httpRequest.getRemoteAddr();
        logger.info("DAILY LIMIT UPDATE REQUEST - chatId: {}, requested value: {}, client: {}",
                chatId, request.getPermanentLimitIncrease(), clientInfo);

        if (request.getPermanentLimitIncrease() != null && request.getPermanentLimitIncrease() == 0) {
            try {
                Long currentLimit = userService.getUserDetails(chatId).getPermanentLimitIncrease();
                if (currentLimit != null && currentLimit > 0) {
                    logger.warn("ATTEMPT TO RESET DAILY LIMIT VIA API - chatId: {}, current: {}, requested: 0, client: {}",
                            chatId, currentLimit, clientInfo);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(String.format("Cannot reset permanent limit increase to 0 when current limit is %d. " +
                                    "Permanent limits should never be reset automatically. " +
                                    "If this is intentional, contact system administrator.", currentLimit));
                }
            } catch (Exception e) {
                logger.debug("Could not pre-validate daily limit reset for chatId {}: {}", chatId, e.getMessage());
            }
        }

        try {
            com.example.shade.model.UserLimitIncrease limit = userService.updateLimit(chatId, request.getPermanentLimitIncrease());
            Long effectiveDailyLimit = dailyStatsService.getEffectiveDailyLimit(chatId);
            DailyLimitUpdateResponse response = DailyLimitUpdateResponse.builder()
                    .permanentLimitIncrease(limit.getAccumulatedLimitIncrease().setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                    .effectiveDailyLimit(effectiveDailyLimit)
                    .lastUpdated(limit.getLastUpdated())
                    .build();
            logger.info("DAILY LIMIT UPDATE SUCCESS - chatId: {}, permanentLimit: {}, effectiveDailyLimit: {}, client: {}",
                    chatId, limit.getAccumulatedLimitIncrease(), effectiveDailyLimit, clientInfo);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("DAILY LIMIT UPDATE FAILED (Validation) - chatId: {}, error: {}, client: {}",
                    chatId, e.getMessage(), clientInfo);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("DAILY LIMIT UPDATE FAILED (Error) - chatId: {}, error: {}, client: {}",
                    chatId, e.getMessage(), clientInfo);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating daily limit: " + e.getMessage());
        }
    }

    @PostMapping("/{chatId}/block")
    public ResponseEntity<?> blockUser(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            userService.blockUser(chatId);
            return ResponseEntity.ok("User blocked successfully: " + chatId);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error blocking user: " + e.getMessage());
        }
    }

    @PostMapping("/{chatId}/unblock")
    public ResponseEntity<?> unblockUser(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            userService.unblockUser(chatId);
            return ResponseEntity.ok("User unblocked successfully: " + chatId);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error unblocking user: " + e.getMessage());
        }
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long chatId,
            @RequestParam(required = false, defaultValue = "soft") String deleteType,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        if (!"soft".equalsIgnoreCase(deleteType) && !"hard".equalsIgnoreCase(deleteType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid deleteType. Must be 'soft' or 'hard'");
        }
        
        try {
            userService.deleteUser(chatId, deleteType);
            return ResponseEntity.ok("User deleted successfully (type: " + deleteType + "): " + chatId);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting user: " + e.getMessage());
        }
    }

    @PutMapping("/{chatId}/language")
    public ResponseEntity<?> updateLanguage(
            @PathVariable Long chatId,
            @RequestBody UpdateLanguageRequest request,
            HttpServletRequest httpRequest) {
        
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            com.example.shade.model.User user = userService.updateLanguage(chatId, request.getLanguage());
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating language: " + e.getMessage());
        }
    }

    @PostMapping("/{chatId}/reset-daily-stats")
    public ResponseEntity<?> resetDailyStats(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            userService.resetDailyStats(chatId);
            return ResponseEntity.ok("Daily stats reset successfully for user: " + chatId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error resetting daily stats: " + e.getMessage());
        }
    }

    @PostMapping("/{chatId}/reset-balance")
    public ResponseEntity<?> resetBalance(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            userService.resetBalance(chatId);
            return ResponseEntity.ok("Balance and tickets reset successfully for user: " + chatId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error resetting balance: " + e.getMessage());
        }
    }

    @GetMapping("/{chatId}/summary")
    public ResponseEntity<?> getUserSummary(
            @PathVariable Long chatId,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            UserSummaryDTO summary = userService.getUserSummary(chatId);
            return ResponseEntity.ok(summary);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching user summary: " + e.getMessage());
        }
    }

    @GetMapping("/{chatId}/daily-stats")
    public ResponseEntity<?> getUserDailyStats(
            @PathVariable Long chatId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<DailyUserStatsDTO> statsPage = userService.getUserDailyStats(chatId, date, startDate, endDate, pageable);
            return ResponseEntity.ok(statsPage);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching daily stats: " + e.getMessage());
        }
    }

    @PostMapping("/bulk-block")
    public ResponseEntity<?> bulkBlockUsers(
            @RequestBody BulkOperationRequest request,
            HttpServletRequest httpRequest) {
        
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        if (request.getChatIds() == null || request.getChatIds().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("chatIds list cannot be empty");
        }
        
        try {
            List<Long> blocked = userService.bulkBlockUsers(request.getChatIds());
            return ResponseEntity.ok("Bulk block completed. Blocked: " + blocked.size() + " users. IDs: " + blocked);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error in bulk block operation: " + e.getMessage());
        }
    }

    @PostMapping("/bulk-unblock")
    public ResponseEntity<?> bulkUnblockUsers(
            @RequestBody BulkOperationRequest request,
            HttpServletRequest httpRequest) {
        
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        
        if (request.getChatIds() == null || request.getChatIds().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("chatIds list cannot be empty");
        }
        
        try {
            List<Long> unblocked = userService.bulkUnblockUsers(request.getChatIds());
            return ResponseEntity.ok("Bulk unblock completed. Unblocked: " + unblocked.size() + " users. IDs: " + unblocked);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error in bulk unblock operation: " + e.getMessage());
        }
    }
}
