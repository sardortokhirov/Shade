package com.example.shade.controller;

import com.example.shade.dto.*;
import com.example.shade.model.ApkLinkBotConfig;
import com.example.shade.model.ApkLinkPlatform;
import com.example.shade.service.ApkLinkBotConfigService;
import com.example.shade.service.ApkLinkPlatformService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/apk-link-bot")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApkLinkBotController {

    private final ApkLinkBotConfigService configService;
    private final ApkLinkPlatformService platformService;

    private boolean authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String credentials = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = credentials.split(":");
            return parts.length == 2 && "MaxUp1000".equals(parts[0]) && "MaxUp1000998905982808".equals(parts[1]);
        }
        return false;
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ApkLinkBotConfig config = configService.getConfig().orElse(null);
        if (config == null) {
            return ResponseEntity.ok(ApkLinkBotConfigDTO.builder()
                    .botTokenMasked(null)
                    .cooldownPrivateMinutes(null)
                    .cooldownGroupMinutes(null)
                    .build());
        }
        ApkLinkBotConfigDTO dto = ApkLinkBotConfigDTO.builder()
                .botTokenMasked(ApkLinkBotConfigService.maskToken(config.getBotToken()))
                .cooldownPrivateMinutes(config.getCooldownPrivateMinutes())
                .cooldownGroupMinutes(config.getCooldownGroupMinutes())
                .build();
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody ApkLinkBotConfigRequest body, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ApkLinkBotConfig saved = configService.saveConfig(
                body.getBotToken(),
                body.getCooldownPrivateMinutes(),
                body.getCooldownGroupMinutes()
        );
        ApkLinkBotConfigDTO dto = ApkLinkBotConfigDTO.builder()
                .botTokenMasked(ApkLinkBotConfigService.maskToken(saved.getBotToken()))
                .cooldownPrivateMinutes(saved.getCooldownPrivateMinutes())
                .cooldownGroupMinutes(saved.getCooldownGroupMinutes())
                .build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/platforms")
    public ResponseEntity<?> listPlatforms(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        List<ApkLinkPlatformDTO> list = platformService.findAllPlatforms().stream()
                .map(this::toPlatformDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/platforms")
    public ResponseEntity<?> createPlatform(@RequestBody ApkLinkPlatformRequest body, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ApkLinkPlatform p = platformService.createPlatform(
                body.getName(),
                body.getLinkUrl(),
                body.getApkFileId(),
                body.getApkUrl(),
                body.getSortOrder()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlatformDTO(p));
    }

    @PutMapping("/platforms/{id}")
    public ResponseEntity<?> updatePlatform(@PathVariable Long id, @RequestBody ApkLinkPlatformRequest body,
                                            HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        return platformService.updatePlatform(id, body.getName(), body.getLinkUrl(),
                        body.getApkFileId(), body.getApkUrl(), body.getSortOrder())
                .map(p -> ResponseEntity.ok(toPlatformDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/platforms/{id}")
    public ResponseEntity<?> deletePlatform(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (platformService.findPlatformById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        platformService.deletePlatform(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/platforms/{platformId}/keywords")
    public ResponseEntity<?> listKeywords(@PathVariable Long platformId, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!platformService.findPlatformById(platformId).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        List<ApkLinkKeywordDTO> list = platformService.getKeywords(platformId).stream()
                .map(k -> ApkLinkKeywordDTO.builder().id(k.getId()).keyword(k.getKeyword()).build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/platforms/{platformId}/keywords")
    public ResponseEntity<?> addKeyword(@PathVariable Long platformId, @RequestBody ApkLinkKeywordRequest body,
                                        HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (!platformService.findPlatformById(platformId).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Optional<ApkLinkKeywordDTO> created = platformService.addKeyword(platformId, body.getKeyword())
                .map(k -> ApkLinkKeywordDTO.builder().id(k.getId()).keyword(k.getKeyword()).build());
        if (created.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(created.get());
        }
        return ResponseEntity.badRequest().body("Invalid or duplicate keyword");
    }

    @DeleteMapping("/platforms/{platformId}/keywords/{keyword}")
    public ResponseEntity<?> removeKeyword(@PathVariable Long platformId, @PathVariable String keyword,
                                           HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        boolean removed = platformService.removeKeyword(platformId, keyword);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private ApkLinkPlatformDTO toPlatformDTO(ApkLinkPlatform p) {
        return ApkLinkPlatformDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .linkUrl(p.getLinkUrl())
                .apkFileId(p.getApkFileId())
                .apkUrl(p.getApkUrl())
                .sortOrder(p.getSortOrder())
                .build();
    }
}
