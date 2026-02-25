package com.example.shade.service;

import com.example.shade.model.ApkLinkKeyword;
import com.example.shade.model.ApkLinkPlatform;
import com.example.shade.repository.ApkLinkKeywordRepository;
import com.example.shade.repository.ApkLinkPlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApkLinkPlatformService {

    private final ApkLinkPlatformRepository platformRepository;
    private final ApkLinkKeywordRepository keywordRepository;

    public List<ApkLinkPlatform> findAllPlatforms() {
        return platformRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    public Optional<ApkLinkPlatform> findPlatformById(Long id) {
        return platformRepository.findById(id);
    }

    @Transactional
    public ApkLinkPlatform createPlatform(String name, String linkUrl, String apkFileId, String apkUrl,
            Integer sortOrder, String apkFileName, String apkCaption, String linkKeyword, String apkKeyword) {
        ApkLinkPlatform p = ApkLinkPlatform.builder()
                .name(name)
                .linkUrl(linkUrl)
                .apkFileId(apkFileId)
                .apkUrl(apkUrl)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .apkFileName(apkFileName)
                .apkCaption(apkCaption)
                .linkKeyword(linkKeyword)
                .apkKeyword(apkKeyword)
                .build();
        return platformRepository.save(p);
    }

    @Transactional
    public Optional<ApkLinkPlatform> updatePlatform(Long id, String name, String linkUrl, String apkFileId,
            String apkUrl, Integer sortOrder, String apkFileName, String apkCaption, String linkKeyword,
            String apkKeyword) {
        return platformRepository.findById(id).map(p -> {
            if (name != null)
                p.setName(name);
            if (linkUrl != null)
                p.setLinkUrl(linkUrl);
            if (apkFileId != null)
                p.setApkFileId(apkFileId);
            if (apkUrl != null)
                p.setApkUrl(apkUrl);
            if (sortOrder != null)
                p.setSortOrder(sortOrder);
            if (apkFileName != null)
                p.setApkFileName(apkFileName);
            if (apkCaption != null)
                p.setApkCaption(apkCaption.isEmpty() ? null : apkCaption);
            if (linkKeyword != null)
                p.setLinkKeyword(linkKeyword.isEmpty() ? null : linkKeyword);
            if (apkKeyword != null)
                p.setApkKeyword(apkKeyword.isEmpty() ? null : apkKeyword);
            return platformRepository.save(p);
        });
    }

    @Transactional
    public Optional<ApkLinkPlatform> updateApkFileId(Long platformId, String apkFileId) {
        if (platformId == null || apkFileId == null || apkFileId.isBlank())
            return Optional.empty();
        return platformRepository.findById(platformId).map(p -> {
            p.setApkFileId(apkFileId);
            return platformRepository.save(p);
        });
    }

    @Transactional
    public void deletePlatform(Long id) {
        keywordRepository.findByPlatformIdOrderByKeywordAsc(id).forEach(keywordRepository::delete);
        platformRepository.deleteById(id);
    }

    public List<ApkLinkKeyword> getKeywords(Long platformId) {
        return keywordRepository.findByPlatformIdOrderByKeywordAsc(platformId);
    }

    public static String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase();
    }

    @Transactional
    public Optional<ApkLinkKeyword> addKeyword(Long platformId, String keyword) {
        String norm = normalizeKeyword(keyword);
        if (norm.isEmpty())
            return Optional.empty();
        if (!platformRepository.existsById(platformId))
            return Optional.empty();
        if (keywordRepository.existsByPlatformIdAndKeywordIgnoreCase(platformId, norm))
            return keywordRepository.findByPlatformIdAndKeywordIgnoreCase(platformId, norm);
        ApkLinkKeyword k = ApkLinkKeyword.builder().platformId(platformId).keyword(norm).build();
        return Optional.of(keywordRepository.save(k));
    }

    @Transactional
    public boolean removeKeyword(Long platformId, String keyword) {
        String norm = normalizeKeyword(keyword);
        return keywordRepository.findByPlatformIdAndKeywordIgnoreCase(platformId, norm)
                .map(k -> {
                    keywordRepository.delete(k);
                    return true;
                })
                .orElse(false);
    }

    public Optional<ApkLinkPlatform> findPlatformByLinkKeyword(String text) {
        String norm = normalizeKeyword(text);
        if (norm.isEmpty())
            return Optional.empty();

        return platformRepository.findAll().stream()
                .filter(p -> p.getLinkKeyword() != null &&
                        java.util.Arrays.stream(p.getLinkKeyword().split(","))
                                .map(ApkLinkPlatformService::normalizeKeyword)
                                .anyMatch(k -> k.equalsIgnoreCase(norm)))
                .findFirst();
    }

    public Optional<ApkLinkPlatform> findPlatformByApkKeyword(String text) {
        String norm = normalizeKeyword(text);
        if (norm.isEmpty())
            return Optional.empty();

        return platformRepository.findAll().stream()
                .filter(p -> p.getApkKeyword() != null &&
                        java.util.Arrays.stream(p.getApkKeyword().split(","))
                                .map(ApkLinkPlatformService::normalizeKeyword)
                                .anyMatch(k -> k.equalsIgnoreCase(norm)))
                .findFirst();
    }
}
