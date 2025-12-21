package com.example.shade.controller;

import com.example.shade.model.UserPlatformPermission;
import com.example.shade.repository.UserPlatformPermissionRepository;
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
@RequestMapping("/api/admin/user-permissions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserPermissionController {

    private final UserPlatformPermissionRepository permissionRepository;

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
    public ResponseEntity<Page<UserPlatformPermission>> getAllPermissions(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(permissionRepository.findAll(pageable));
    }

    @PostMapping
    public ResponseEntity<String> savePermission(
            HttpServletRequest request,
            @RequestParam String userId,
            @RequestParam(defaultValue = "true") boolean canTopUp,
            @RequestParam(defaultValue = "true") boolean canWithdraw,
            @RequestParam(defaultValue = "true") boolean canBonusTopUp) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }

        UserPlatformPermission permission = permissionRepository.findByUserId(userId)
                .orElse(UserPlatformPermission.builder().userId(userId).build());

        permission.setCanTopUp(canTopUp);
        permission.setCanWithdraw(canWithdraw);
        permission.setCanBonusTopUp(canBonusTopUp);

        permissionRepository.save(permission);
        return ResponseEntity.ok("✅ Ruxsatlar saqlandi: " + userId);
    }

    @DeleteMapping
    public ResponseEntity<String> deletePermission(HttpServletRequest request, @RequestParam String userId) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ Autentifikatsiya xatosi");
        }
        permissionRepository.deleteByUserId(userId);
        return ResponseEntity.ok("✅ Ruxsatlar o'chirildi (defaultga qaytdi): " + userId);
    }
}
