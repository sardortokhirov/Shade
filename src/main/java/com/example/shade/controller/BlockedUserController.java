package com.example.shade.controller;

import com.example.shade.service.BlockedUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/admin/blocked-users")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class BlockedUserController {

    private final BlockedUserService blockedUserService;
    private final com.example.shade.repository.UserRepository userRepository;

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
    public ResponseEntity<Page<com.example.shade.dto.UserStatusDTO>> getAllUsers(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userRepository.findAllWithBlockedStatus(pageable));
    }

    @PostMapping("/unblock")
    public ResponseEntity<String> unblockUser(HttpServletRequest request, @RequestParam Long chatId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        BlockedUserService.UnblockChatResult result = blockedUserService.unblockChat(chatId);
        if (result == BlockedUserService.UnblockChatResult.NOT_BLOCKED) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Foydalanuvchi bloklanganlar ro‘yxatida emas");
        }
        return ResponseEntity.ok("✅ Foydalanuvchi blokdan chiqarildi: " + chatId);
    }

    @PostMapping("/block-phone")
    public ResponseEntity<String> blockPhone(HttpServletRequest request, @RequestParam String phone) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        if (!blockedUserService.blockPhoneNumber(phone)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Telefon raqami noto‘g‘ri yoki bo‘sh");
        }
        return ResponseEntity.ok("✅ Telefon raqami bloklandi: " + phone.trim());
    }

    @PostMapping("/unblock-phone")
    public ResponseEntity<String> unblockPhone(HttpServletRequest request, @RequestParam String phone) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        if (!blockedUserService.unblockPhoneNumber(phone)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Bu raqam bloklanganlar ro‘yxatida emas");
        }
        return ResponseEntity.ok("✅ Telefon raqami blokdan chiqarildi: " + phone.trim());
    }
}
