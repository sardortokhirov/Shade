package com.example.shade.controller;

import com.example.shade.model.AdminChat;
import com.example.shade.service.AdminLogBotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AdminController {
    private final AdminLogBotService adminLogBotService;

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
    public ResponseEntity<?> getAdminChats(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Autentifikatsiya xatosi: Noto‘g‘ri foydalanuvchi yoki parol");
        }
        List<AdminChat> adminChats = adminLogBotService.getAllAdminChats();
        return ResponseEntity.ok(adminChats);
    }

    @PostMapping("/chats")
    public ResponseEntity<String> createAdminChat(HttpServletRequest request, @RequestParam Long chatId,
            @RequestParam(defaultValue = "true") boolean receiveNotifications) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Autentifikatsiya xatosi: Noto‘g‘ri foydalanuvchi yoki parol");
        }
        boolean created = adminLogBotService.createAdminChat(chatId, receiveNotifications);
        if (created) {
            return ResponseEntity.ok("✅ Admin chat qo‘shildi: " + chatId);
        }
        return ResponseEntity.ok("✅ Admin chat allaqachon mavjud, bildirishnomalar yangilandi: " + chatId);
    }

    @PostMapping("/notifications")
    public ResponseEntity<String> toggleNotifications(HttpServletRequest request, @RequestParam Long chatId,
            @RequestParam boolean enable) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Autentifikatsiya xatosi: Noto‘g‘ri foydalanuvchi yoki parol");
        }
        adminLogBotService.toggleNotifications(chatId, enable);
        return ResponseEntity.ok(enable ? "✅ Bildirishnomalar yoqildi" : "🛑 Bildirishnomalar o‘chirildi");
    }

    @PutMapping("/chats/{chatId}")
    public ResponseEntity<String> updateNotifications(HttpServletRequest request, @PathVariable Long chatId,
            @RequestParam boolean enable) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Autentifikatsiya xatosi: Noto‘g‘ri foydalanuvchi yoki parol");
        }
        boolean updated = adminLogBotService.updateNotifications(chatId, enable);
        if (updated) {
            return ResponseEntity.ok(enable ? "✅ Bildirishnomalar yoqildi" : "🛑 Bildirishnomalar o‘chirildi");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Chat ID " + chatId + " topilmadi");
    }

    @DeleteMapping("/chats/{chatId}")
    public ResponseEntity<String> deleteAdminChat(HttpServletRequest request, @PathVariable Long chatId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Autentifikatsiya xatosi: Noto‘g‘ri foydalanuvchi yoki parol");
        }
        boolean deleted = adminLogBotService.deleteAdminChat(chatId);
        if (deleted) {
            return ResponseEntity.ok("✅ Chat ID " + chatId + " adminlar ro‘yxatidan o‘chirildi");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Chat ID " + chatId + " topilmadi");
    }
}