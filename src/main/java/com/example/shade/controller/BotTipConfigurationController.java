package com.example.shade.controller;

import com.example.shade.model.BotTipConfiguration;
import com.example.shade.service.BotTipConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.shade.dto.BotTipConfigStatusDTO;
import com.example.shade.dto.BotTipStatsDTO;
import com.example.shade.model.HizmatRequest;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/bot-tip-config")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class BotTipConfigurationController {

    private final BotTipConfigurationService configurationService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        try {
            String encodedCredentials = authHeader.substring(6);
            String credentials = new String(Base64.getDecoder().decode(encodedCredentials));
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                return false;
            }

            String username = parts[0].trim();
            String password = parts[1].trim();

            return "MaxUp1000".equals(username) && "MaxUp1000998905982808".equals(password);
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping
    public ResponseEntity<BotTipConfiguration> getConfiguration(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.getConfiguration());
    }

    @GetMapping("/status")
    public ResponseEntity<BotTipConfigStatusDTO> getBonusStatus(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.getBonusStatus());
    }

    @PostMapping
    public ResponseEntity<BotTipConfiguration> createConfiguration(
            @RequestBody BotTipConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.updateConfiguration(config));
    }

    @PutMapping
    public ResponseEntity<BotTipConfiguration> updateConfigurationPut(
            @RequestBody BotTipConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.updateConfiguration(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BotTipConfiguration> updateConfiguration(
            @PathVariable Long id,
            @RequestBody BotTipConfiguration config,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        config.setId(id);
        return ResponseEntity.ok(configurationService.updateConfiguration(config));
    }

    @GetMapping("/stats")
    public ResponseEntity<BotTipStatsDTO> getTipStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.getTipStats(startDate, endDate));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<HizmatRequest>> getTipTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(configurationService.getTipTransactions(startDate, endDate));
    }
}
