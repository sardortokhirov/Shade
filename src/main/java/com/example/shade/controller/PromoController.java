package com.example.shade.controller;

import com.example.shade.dto.PromoChatSummaryDTO;
import com.example.shade.dto.PromoPlatformLinkDTO;
import com.example.shade.dto.PromoPlatformLinkRequest;
import com.example.shade.dto.PromoSearchResultDTO;
import com.example.shade.service.PromoWhitelistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/admin/promo")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class PromoController {

    private final PromoWhitelistService promoWhitelistService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000998905982808".equals(parts[1]);
        }
        return false;
    }

    @GetMapping("/chats")
    public ResponseEntity<Page<PromoChatSummaryDTO>> getChats(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(promoWhitelistService.getChats(pageable));
    }

    @PostMapping("/chats")
    public ResponseEntity<String> addChat(HttpServletRequest request, @RequestParam Long chatId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        promoWhitelistService.addChat(chatId);
        return ResponseEntity.ok("✅ Chat ID ruxsat etildi: " + chatId);
    }

    @DeleteMapping("/chats")
    public ResponseEntity<String> deleteChat(HttpServletRequest request, @RequestParam Long chatId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        promoWhitelistService.deleteChat(chatId);
        return ResponseEntity.ok("✅ Chat ID o'chirildi: " + chatId);
    }

    @GetMapping("/chats/{chatId}/links")
    public ResponseEntity<List<PromoPlatformLinkDTO>> getLinks(
            HttpServletRequest request, @PathVariable Long chatId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(promoWhitelistService.getLinks(chatId));
    }

    @PostMapping("/chats/{chatId}/links")
    public ResponseEntity<PromoPlatformLinkDTO> addLink(
            HttpServletRequest request,
            @PathVariable Long chatId,
            @RequestBody PromoPlatformLinkRequest body) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PromoPlatformLinkDTO saved = promoWhitelistService.addLink(
                chatId, body.getPlatformUserId(), body.getPlatformName());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/chats/{chatId}/links/{linkId}")
    public ResponseEntity<String> deleteLink(
            HttpServletRequest request,
            @PathVariable Long chatId,
            @PathVariable Long linkId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        promoWhitelistService.deleteLink(chatId, linkId);
        return ResponseEntity.ok("✅ Bog'lanish o'chirildi");
    }

    @GetMapping("/search")
    public ResponseEntity<PromoSearchResultDTO> search(
            HttpServletRequest request,
            @RequestParam(required = false) Long chatId,
            @RequestParam(required = false) String platformUserId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(promoWhitelistService.search(chatId, platformUserId));
    }
}
