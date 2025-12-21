package com.example.shade.controller;

import com.example.shade.model.BlockedUser;
import com.example.shade.repository.BlockedUserRepository;
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
@CrossOrigin(origins = "*")
public class BlockedUserController {

    private final BlockedUserRepository blockedUserRepository;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<Page<BlockedUser>> getAllBlockedUsers(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(blockedUserRepository.findAll(pageable));
    }

    @PostMapping("/unblock")
    public ResponseEntity<String> unblockUser(HttpServletRequest request, @RequestParam Long chatId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        if (!blockedUserRepository.existsById(chatId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Foydalanuvchi topilmadi");
        }
        blockedUserRepository.deleteById(chatId);
        return ResponseEntity.ok("✅ Foydalanuvchi blokdan chiqarildi: " + chatId);
    }
}
