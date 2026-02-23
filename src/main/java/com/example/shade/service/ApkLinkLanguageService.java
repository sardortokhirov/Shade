package com.example.shade.service;

import com.example.shade.model.ApkLinkUserPreference;
import com.example.shade.repository.ApkLinkUserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApkLinkLanguageService {

    private static final String DEFAULT_LANG = "uz";

    private final ApkLinkUserPreferenceRepository preferenceRepository;

    public Optional<String> getLanguageCode(Long chatId) {
        if (chatId == null) return Optional.empty();
        return preferenceRepository.findById(chatId)
                .map(ApkLinkUserPreference::getLanguageCode);
    }

    @Transactional
    public void setLanguage(Long chatId, String languageCode) {
        if (chatId == null || languageCode == null) return;
        String code = "ru".equalsIgnoreCase(languageCode) ? "ru" : "uz";
        ApkLinkUserPreference pref = preferenceRepository.findById(chatId)
                .orElse(ApkLinkUserPreference.builder().chatId(chatId).build());
        pref.setLanguageCode(code);
        preferenceRepository.save(pref);
    }

    public Locale getLocale(Long chatId) {
        String code = getLanguageCode(chatId).orElse(DEFAULT_LANG);
        return Locale.forLanguageTag(code);
    }
}
