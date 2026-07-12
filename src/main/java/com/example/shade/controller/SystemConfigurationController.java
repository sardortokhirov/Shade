package com.example.shade.controller;

import com.example.shade.model.SystemConfiguration;
import com.example.shade.service.SystemConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemConfigurationController {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationController.class);
    private final SystemConfigurationService configurationService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
                String[] parts = credentials.split(":");
                return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid Basic auth encoding: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<SystemConfiguration> getConfiguration(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.getConfiguration());
    }

    @PostMapping
    public ResponseEntity<SystemConfiguration> createConfiguration(
            @RequestBody SystemConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        SystemConfiguration saved = configurationService.updateConfiguration(config);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemConfiguration> updateConfiguration(
            @PathVariable Long id,
            @RequestBody SystemConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        config.setId(id);
        SystemConfiguration saved = configurationService.updateConfiguration(config);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/wallet-withdraw-ratio")
    public ResponseEntity<?> getWalletWithdrawRatio(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(java.util.Map.of("walletWithdrawRatio", configurationService.getWalletWithdrawRatio()));
    }

    @PatchMapping("/wallet-withdraw-ratio")
    public ResponseEntity<?> setWalletWithdrawRatio(@RequestParam Long ratio, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (ratio == null || ratio <= 0) {
            return ResponseEntity.badRequest().body("Ratio must be greater than 0");
        }
        return ResponseEntity.ok(configurationService.setWalletWithdrawRatio(ratio));
    }

    @GetMapping("/wallet-min-withdraw")
    public ResponseEntity<?> getWalletMinWithdraw(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(java.util.Map.of("walletMinWithdrawAmount", configurationService.getWalletMinWithdrawAmount()));
    }

    @PatchMapping("/wallet-min-withdraw")
    public ResponseEntity<?> setWalletMinWithdraw(@RequestParam Long amount, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body("Amount must be greater than 0");
        }
        return ResponseEntity.ok(configurationService.setWalletMinWithdrawAmount(amount));
    }

    @GetMapping("/wallet-transfer-amount-limits")
    public ResponseEntity<?> getWalletTransferAmountLimits(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(java.util.Map.of(
                "walletTransferMinAmount", configurationService.getWalletTransferMinAmount(),
                "walletTransferMaxAmount", configurationService.getWalletTransferMaxAmount()));
    }

    @PatchMapping("/wallet-transfer-min")
    public ResponseEntity<?> setWalletTransferMin(@RequestParam Long amount, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body("Amount must be greater than 0");
        }
        return ResponseEntity.ok(configurationService.setWalletTransferMinAmount(amount));
    }

    @PatchMapping("/wallet-transfer-max")
    public ResponseEntity<?> setWalletTransferMax(@RequestParam Long amount, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().body("Amount must be greater than 0");
        }
        return ResponseEntity.ok(configurationService.setWalletTransferMaxAmount(amount));
    }
}
