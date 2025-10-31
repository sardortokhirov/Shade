package com.example.shade.controller;

import com.example.shade.dto.PlatformRequest;
import com.example.shade.model.Currency;
import com.example.shade.model.Platform;
import com.example.shade.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PlatformController {

    private final PlatformService platformService;

    // This authentication logic remains unchanged.
    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000".equals(parts[1]);
        }
        return false;
    }


    @GetMapping("/platforms")
    public ResponseEntity<List<Platform>> getPlatforms(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(platformService.getAllPlatforms());
    }

    @GetMapping("/platforms/{id}")
    public ResponseEntity<Platform> getPlatform(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        return ResponseEntity.ok(platformService.getPlatformById(id));
    }

    // SIMPLIFIED: The controller now passes the whole request to the service.
    @PostMapping("/platforms")
    public ResponseEntity<Platform> createPlatform(@RequestBody PlatformRequest request, HttpServletRequest httpRequest) {
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        Platform platform = platformService.createPlatform(request);
        return ResponseEntity.ok(platform);
    }

    // SIMPLIFIED: The controller now passes the whole request to the service.
    @PutMapping("/platforms/{id}")
    public ResponseEntity<Platform> updatePlatform(@PathVariable Long id, @RequestBody PlatformRequest request, HttpServletRequest httpRequest) {
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        Platform platform = platformService.updatePlatform(id, request);
        return ResponseEntity.ok(platform);
    }

    @DeleteMapping("/platforms/{id}")
    public ResponseEntity<Void> deletePlatform(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!authenticate(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        platformService.deletePlatform(id);
        return ResponseEntity.noContent().build();
    }

    // DTO (Data Transfer Object) for receiving requests.
    // This now perfectly matches the structure of the frontend payload.

}