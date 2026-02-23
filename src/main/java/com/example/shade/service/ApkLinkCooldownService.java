package com.example.shade.service;

import com.example.shade.model.ApkLinkGroupCooldown;
import com.example.shade.model.ApkLinkUserCooldown;
import com.example.shade.repository.ApkLinkGroupCooldownRepository;
import com.example.shade.repository.ApkLinkUserCooldownRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApkLinkCooldownService {

    private final ApkLinkUserCooldownRepository userCooldownRepository;
    private final ApkLinkGroupCooldownRepository groupCooldownRepository;

    public Optional<Long> getRemainingMinutesUser(Long userId, int cooldownMinutes) {
        return userCooldownRepository.findById(userId)
                .map(c -> {
                    Instant end = c.getLastRequestAt().plusSeconds(cooldownMinutes * 60L);
                    if (Instant.now().isBefore(end)) {
                        return Optional.of((end.getEpochSecond() - Instant.now().getEpochSecond()) / 60);
                    }
                    return Optional.<Long>empty();
                })
                .orElse(Optional.empty());
    }

    @Transactional
    public void applyUserCooldown(Long userId) {
        ApkLinkUserCooldown c = userCooldownRepository.findById(userId)
                .orElse(ApkLinkUserCooldown.builder().userId(userId).build());
        c.setLastRequestAt(Instant.now());
        userCooldownRepository.save(c);
    }

    public Optional<Long> getRemainingMinutesGroup(Long chatId, int cooldownMinutes) {
        return groupCooldownRepository.findById(chatId)
                .map(c -> {
                    Instant end = c.getLastRequestAt().plusSeconds(cooldownMinutes * 60L);
                    if (Instant.now().isBefore(end)) {
                        return Optional.of((end.getEpochSecond() - Instant.now().getEpochSecond()) / 60);
                    }
                    return Optional.<Long>empty();
                })
                .orElse(Optional.empty());
    }

    @Transactional
    public void applyGroupCooldown(Long chatId) {
        ApkLinkGroupCooldown c = groupCooldownRepository.findById(chatId)
                .orElse(ApkLinkGroupCooldown.builder().chatId(chatId).build());
        c.setLastRequestAt(Instant.now());
        groupCooldownRepository.save(c);
    }
}
