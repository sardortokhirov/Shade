package com.example.shade.controller;

import com.example.shade.dto.*;
import com.example.shade.model.ApkLinkBotConfig;
import com.example.shade.model.ApkLinkInvite;
import com.example.shade.model.ApkLinkPlatform;
import com.example.shade.service.ApkLinkBotConfigService;
import com.example.shade.service.ApkLinkInviteService;
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
    private final ApkLinkInviteService inviteService;

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
                    .channelKeywordAllApk(null)
                    .groupKeywordAllApk(null)
                    .apkChannelChatId(null)
                    .apkChannelMessageId(null)
                    .apkChannelMessageLink(null)
                    .mainApkChannelChatId(null)
                    .build());
        }
        ApkLinkBotConfigDTO dto = ApkLinkBotConfigDTO.builder()
                .botTokenMasked(ApkLinkBotConfigService.maskToken(config.getBotToken()))
                .cooldownPrivateMinutes(config.getCooldownPrivateMinutes())
                .cooldownGroupMinutes(config.getCooldownGroupMinutes())
                .channelKeywordAllApk(config.getChannelKeywordAllApk())
                .groupKeywordAllApk(config.getGroupKeywordAllApk())
                .apkChannelChatId(config.getApkChannelChatId())
                .apkChannelMessageId(config.getApkChannelMessageId())
                .apkChannelMessageLink(configService.getApkChannelMessageLink().orElse(null))
                .mainApkChannelChatId(config.getMainApkChannelChatId())
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
                body.getCooldownGroupMinutes(),
                body.getChannelKeywordAllApk(),
                body.getGroupKeywordAllApk(),
                null,
                null
        );
        ApkLinkBotConfigDTO dto = ApkLinkBotConfigDTO.builder()
                .botTokenMasked(ApkLinkBotConfigService.maskToken(saved.getBotToken()))
                .cooldownPrivateMinutes(saved.getCooldownPrivateMinutes())
                .cooldownGroupMinutes(saved.getCooldownGroupMinutes())
                .channelKeywordAllApk(saved.getChannelKeywordAllApk())
                .groupKeywordAllApk(saved.getGroupKeywordAllApk())
                .apkChannelChatId(saved.getApkChannelChatId())
                .apkChannelMessageId(saved.getApkChannelMessageId())
                .apkChannelMessageLink(configService.getApkChannelMessageLink().orElse(null))
                .mainApkChannelChatId(saved.getMainApkChannelChatId())
                .build();
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/config/main-apk-channel")
    public ResponseEntity<?> setMainApkChannel(@RequestBody MainApkChannelRequest body, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ApkLinkBotConfig saved = configService.setMainApkChannelChatId(body.getMainApkChannelChatId());
        ApkLinkBotConfigDTO dto = ApkLinkBotConfigDTO.builder()
                .botTokenMasked(ApkLinkBotConfigService.maskToken(saved.getBotToken()))
                .cooldownPrivateMinutes(saved.getCooldownPrivateMinutes())
                .cooldownGroupMinutes(saved.getCooldownGroupMinutes())
                .channelKeywordAllApk(saved.getChannelKeywordAllApk())
                .groupKeywordAllApk(saved.getGroupKeywordAllApk())
                .apkChannelChatId(saved.getApkChannelChatId())
                .apkChannelMessageId(saved.getApkChannelMessageId())
                .apkChannelMessageLink(configService.getApkChannelMessageLink().orElse(null))
                .mainApkChannelChatId(saved.getMainApkChannelChatId())
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
                body.getSortOrder(),
                body.getApkFileName()
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
                        body.getApkFileId(), body.getApkUrl(), body.getSortOrder(), body.getApkFileName())
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

    @GetMapping("/channels")
    public ResponseEntity<?> listChannels(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        List<ApkLinkInviteDTO> list = inviteService.findAllChannels().stream()
                .map(this::toInviteDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/channels")
    public ResponseEntity<?> createChannel(@RequestBody ApkLinkInviteRequest body, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ApkLinkInvite invite = inviteService.createChannel(body.getName(), body.getInviteLink(), body.getSortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(toInviteDTO(invite));
    }

    @PutMapping("/channels/{id}")
    public ResponseEntity<?> updateChannel(@PathVariable Long id, @RequestBody ApkLinkInviteRequest body,
                                           HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        return inviteService.updateInvite(id, body.getName(), body.getInviteLink(), body.getSortOrder())
                .map(inv -> ResponseEntity.ok(toInviteDTO(inv)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/channels/{id}")
    public ResponseEntity<?> deleteChannel(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (inviteService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        inviteService.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/groups")
    public ResponseEntity<?> listGroups(HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        List<ApkLinkInviteDTO> list = inviteService.findAllGroups().stream()
                .map(this::toInviteDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/groups")
    public ResponseEntity<?> createGroup(@RequestBody ApkLinkInviteRequest body, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        ApkLinkInvite invite = inviteService.createGroup(body.getName(), body.getInviteLink(), body.getSortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(toInviteDTO(invite));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<?> updateGroup(@PathVariable Long id, @RequestBody ApkLinkInviteRequest body,
                                         HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        return inviteService.updateInvite(id, body.getName(), body.getInviteLink(), body.getSortOrder())
                .map(inv -> ResponseEntity.ok(toInviteDTO(inv)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<?> deleteGroup(@PathVariable Long id, HttpServletRequest request) {
        if (!authenticate(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        if (inviteService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        inviteService.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private ApkLinkInviteDTO toInviteDTO(ApkLinkInvite inv) {
        return ApkLinkInviteDTO.builder()
                .id(inv.getId())
                .name(inv.getName())
                .inviteLink(inv.getInviteLink())
                .type(inv.getType())
                .sortOrder(inv.getSortOrder())
                .build();
    }

    private ApkLinkPlatformDTO toPlatformDTO(ApkLinkPlatform p) {
        return ApkLinkPlatformDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .linkUrl(p.getLinkUrl())
                .apkFileId(p.getApkFileId())
                .apkUrl(p.getApkUrl())
                .sortOrder(p.getSortOrder())
                .apkFileName(p.getApkFileName())
                .build();
    }
}
