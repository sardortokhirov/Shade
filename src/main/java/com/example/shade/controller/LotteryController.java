package com.example.shade.controller;

import com.example.shade.bot.AdminBotMessageSender;
import com.example.shade.bot.MessageSender;
import com.example.shade.dto.LotteryTicketBundleRequest;
import com.example.shade.dto.OverallBalanceTicketsDTO;
import com.example.shade.model.LotteryPrize;
import com.example.shade.model.LotteryTicketBundle;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.LotteryPrizeRepository;
import com.example.shade.service.AdminLogBotService;
import com.example.shade.service.LanguageSessionService;
import com.example.shade.service.LotteryConfigService;
import com.example.shade.service.LotteryService;
import com.example.shade.service.LotteryTicketBundleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LotteryController {
    private final LotteryService lotteryService;
    private final LotteryPrizeRepository lotteryPrizeRepository;
    private final AdminBotMessageSender messageSender;
    private final LanguageSessionService languageSessionService;
    private final AdminLogBotService adminLogBotService;
    private final LotteryTicketBundleService bundleService;
    private final LotteryConfigService configService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000998905982808".equals(parts[1]);
        }
        return false;
    }

    @PostMapping("/lottery/prizes")
    public ResponseEntity<LotteryPrize> addPrize(
            @RequestBody LotteryPrize prize,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        LotteryPrize savedPrize = lotteryPrizeRepository.save(prize);
        return ResponseEntity.ok(savedPrize);
    }

    @GetMapping("/lottery/prizes")
    public ResponseEntity<List<LotteryPrize>> getPrizes(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(lotteryPrizeRepository.findAll());
    }

    @DeleteMapping("/lottery/prizes/{id}")
    public ResponseEntity<Void> deletePrize(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (lotteryPrizeRepository.existsById(id)) {
            lotteryPrizeRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/lottery/balance/{chatId}")
    public ResponseEntity<UserBalance> getBalance(@PathVariable Long chatId) {
        try {
            UserBalance balance = lotteryService.getBalance(chatId);
            return ResponseEntity.ok(balance);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/lottery/tickets/{chatId}")
    public ResponseEntity<Void> deleteTickets(@PathVariable Long chatId, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            lotteryService.deleteTickets(chatId);
            messageSender.sendTextMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.tickets_deleted_success"));

            String logMessage = String.format(
                    "Biletlar o'chirildi ✅\n" +
                            "👤 User ID [%d]\n" +
                            "📅 [%s] ",
                    chatId,
                    LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(logMessage);

            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/lottery/balance/{chatId}")
    public ResponseEntity<Void> deleteBalance(@PathVariable Long chatId, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            lotteryService.deleteBalance(chatId);
            messageSender.sendTextMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.balance_deleted_success"));

            String logMessage = String.format(
                    "Balans o'chirildi ✅\n" +
                            "👤 User ID [%d]\n" +
                            "📅 [%s] ",
                    chatId,
                    LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(logMessage);

            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/lottery/tickets/{chatId}")
    public ResponseEntity<UserBalance> addTickets(@PathVariable Long chatId, @RequestParam Long amount,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            lotteryService.awardTickets(chatId, amount);
            UserBalance balance = lotteryService.getBalance(chatId);
            messageSender.sendTextMessage(chatId,
                    languageSessionService.getTranslation(chatId, "message.tickets_added_success"));
            String logMessage = String.format(
                    "Biletlar qo'shildi ✅\n" +
                            "👤 User ID [%d]\n" +
                            "💸 Qo'shilgan miqdor: %d bilet\n" +
                            "🎟️ Jami biletlar: %d\n" +
                            "💰 Balans: %s\n" +
                            "📅 [%s] ",
                    chatId,
                    amount,
                    balance.getTickets(), // assume getTickets()
                    balance.getBalance(), // adjust field
                    LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            adminLogBotService.sendLog(logMessage); // pass message

            return ResponseEntity.ok(balance);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/lottery/award-random-users")
    public ResponseEntity<Void> awardRandomUsers(
            @RequestParam Long totalUsers,
            @RequestParam Long randomUsers,
            @RequestParam Long amount,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            // REST API call - no admin chat ID or message sender, pass null
            lotteryService.awardRandomUsers(totalUsers, randomUsers, amount, null, null);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/lottery/approved-users-chatids")
    public ResponseEntity<List<Long>> getApprovedUsersChatIds(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        List<Long> chatIds = lotteryService.getAllApprovedUsersChatIds();
        return ResponseEntity.ok(chatIds);
    }

    // NEW ENDPOINT 2: Get user balances with pagination
    @GetMapping("/lottery/balances")
    public ResponseEntity<Page<UserBalance>> getUserBalancesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "chatId") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            Page<UserBalance> balances = lotteryService.getUserBalancesPaginated(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(balances);
        } catch (IllegalArgumentException e) {
            // Catches invalid sortDirection value (e.g., if it's not "ASC" or "DESC")
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/lottery/overall")
    public ResponseEntity<OverallBalanceTicketsDTO> getOverallBalanceAndTickets(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        OverallBalanceTicketsDTO result = lotteryService.getOverallBalanceAndTickets();
        return ResponseEntity.ok(result);
    }

    // Bundle Management Endpoints
    @PostMapping("/lottery/bundles")
    public ResponseEntity<LotteryTicketBundle> createBundle(
            @RequestBody LotteryTicketBundleRequest requestBody,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        // Map DTO to entity
        LotteryTicketBundle bundle = LotteryTicketBundle.builder()
                .ticketQuantity(requestBody.getTickets())
                .price(requestBody.getPrice())
                .isActive(requestBody.getIsActive() != null ? requestBody.getIsActive() : true)
                .displayOrder(requestBody.getDisplayOrder() != null ? requestBody.getDisplayOrder() : 0)
                .build();
        LotteryTicketBundle savedBundle = bundleService.save(bundle);
        return ResponseEntity.ok(savedBundle);
    }

    @GetMapping("/lottery/bundles")
    public ResponseEntity<List<LotteryTicketBundle>> getAllBundles(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(bundleService.getActiveBundles());
    }

    @PutMapping("/lottery/bundles/{id}")
    public ResponseEntity<LotteryTicketBundle> updateBundle(
            @PathVariable Long id,
            @RequestBody LotteryTicketBundleRequest requestBody,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            LotteryTicketBundle existing = bundleService.findById(id);
            if (requestBody.getTickets() != null) {
                existing.setTicketQuantity(requestBody.getTickets());
            }
            if (requestBody.getPrice() != null) {
                existing.setPrice(requestBody.getPrice());
            }
            if (requestBody.getIsActive() != null) {
                existing.setIsActive(requestBody.getIsActive());
            }
            if (requestBody.getDisplayOrder() != null) {
                existing.setDisplayOrder(requestBody.getDisplayOrder());
            }
            LotteryTicketBundle updated = bundleService.save(existing);
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/lottery/bundles/{id}")
    public ResponseEntity<Void> deleteBundle(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            bundleService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/lottery/bundles/{id}/toggle")
    public ResponseEntity<LotteryTicketBundle> toggleBundle(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            LotteryTicketBundle bundle = bundleService.toggleActive(id);
            return ResponseEntity.ok(bundle);
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Purchase Cooldown Endpoints
    @GetMapping("/lottery/purchase-cooldown")
    public ResponseEntity<CooldownResponse> getPurchaseCooldown(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(new CooldownResponse(configService.getPurchaseCooldownSeconds()));
    }

    @PutMapping("/lottery/purchase-cooldown")
    public ResponseEntity<CooldownResponse> setPurchaseCooldown(
            @RequestBody CooldownRequest requestBody,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            configService.setPurchaseCooldownSeconds(requestBody.getCooldownSeconds());
            return ResponseEntity.ok(new CooldownResponse(configService.getPurchaseCooldownSeconds()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Winnings Percentage Endpoints
    @GetMapping("/lottery/winnings-percentage")
    public ResponseEntity<WinningsPercentageResponse> getWinningsPercentage(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(new WinningsPercentageResponse(configService.getWinningsPercentage()));
    }

    @PutMapping("/lottery/winnings-percentage")
    public ResponseEntity<WinningsPercentageResponse> setWinningsPercentage(
            @RequestBody WinningsPercentageRequest requestBody,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        try {
            configService.setWinningsPercentage(requestBody.getPercentage());
            return ResponseEntity.ok(new WinningsPercentageResponse(configService.getWinningsPercentage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // DTOs for request/response
    @Data
    public static class CooldownRequest {
        private Long cooldownSeconds;
    }

    @Data
    public static class CooldownResponse {
        private Long cooldownSeconds;
        public CooldownResponse(Long cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }
    }

    @Data
    public static class WinningsPercentageRequest {
        private BigDecimal percentage;
    }

    @Data
    public static class WinningsPercentageResponse {
        private BigDecimal percentage;
        public WinningsPercentageResponse(BigDecimal percentage) {
            this.percentage = percentage;
        }
    }
}