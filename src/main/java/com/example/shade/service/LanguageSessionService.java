package com.example.shade.service;

import com.example.shade.model.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date-9/3/2025
 * By Sardor Tokhirov
 * Time-7:09 PM (GMT+5)
 */

@Service
@RequiredArgsConstructor
public class LanguageSessionService {
    private final Map<Long, Language> sessionStore = new ConcurrentHashMap<>();


    private final MessageSource messageSource;


    public String getTranslation(Long chatId, String textCode) {
        return getTranslation(chatId, textCode, (Object[]) null);
    }

    public String getTranslation(Long chatId, String textCode, Object... args) {
        Language language = sessionStore.get(chatId);
        Locale locale = language == null ? new Locale("uz") : new Locale(language.getCode());
        try {
            return messageSource.getMessage(textCode, args, locale);
        } catch (Exception e) {
            return "Translation not found for code: " + textCode;
        }
    }

    public void addUserLanguageSession(Long chatId, Language language) {
        sessionStore.put(chatId, language);
    }

    public boolean checkUserUserSession(Long chatId) {
        return sessionStore.containsKey(chatId);
    }
    public void clearSession(Long chatId) {
        sessionStore.remove(chatId);
    }
}
