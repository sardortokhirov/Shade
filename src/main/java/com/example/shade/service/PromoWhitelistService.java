package com.example.shade.service;

import com.example.shade.dto.PromoChatSummaryDTO;
import com.example.shade.dto.PromoPlatformLinkDTO;
import com.example.shade.dto.PromoSearchResultDTO;
import com.example.shade.model.PromoAllowedChat;
import com.example.shade.model.PromoPlatformLink;
import com.example.shade.repository.PromoAllowedChatRepository;
import com.example.shade.repository.PromoPlatformLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromoWhitelistService {

    private static final int PLATFORM_NAME_MAX_LENGTH = 80;

    private final PromoAllowedChatRepository chatRepository;
    private final PromoPlatformLinkRepository linkRepository;

    @Transactional(readOnly = true)
    public Page<PromoChatSummaryDTO> getChats(Pageable pageable) {
        return chatRepository.findAll(pageable).map(chat -> {
            long count = linkRepository.countByChatId(chat.getChatId());
            return PromoChatSummaryDTO.builder()
                    .chatId(chat.getChatId())
                    .linkCount(count)
                    .filled(count > 0)
                    .build();
        });
    }

    @Transactional
    public PromoAllowedChat addChat(Long chatId) {
        if (chatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat ID bo'sh bo'lishi mumkin emas");
        }
        return chatRepository.findByChatId(chatId).orElseGet(() ->
                chatRepository.save(PromoAllowedChat.builder().chatId(chatId).build()));
    }

    @Transactional
    public void deleteChat(Long chatId) {
        if (chatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat ID bo'sh bo'lishi mumkin emas");
        }
        if (!chatRepository.existsByChatId(chatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat ID topilmadi: " + chatId);
        }
        linkRepository.deleteByChatId(chatId);
        chatRepository.deleteByChatId(chatId);
    }

    @Transactional(readOnly = true)
    public List<PromoPlatformLinkDTO> getLinks(Long chatId) {
        requireChat(chatId);
        return linkRepository.findByChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toLinkDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PromoPlatformLinkDTO addLink(Long chatId, String platformUserId, String platformName) {
        requireChat(chatId);
        String trimmedUserId = validatePlatformUserId(platformUserId);
        String trimmedName = validatePlatformName(platformName);

        if (linkRepository.existsByChatIdAndPlatformUserId(chatId, trimmedUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu platforma ID allaqachon bog'langan: " + trimmedUserId);
        }

        PromoPlatformLink saved = linkRepository.save(PromoPlatformLink.builder()
                .chatId(chatId)
                .platformUserId(trimmedUserId)
                .platformName(trimmedName)
                .build());
        return toLinkDto(saved);
    }

    @Transactional
    public void deleteLink(Long chatId, Long linkId) {
        requireChat(chatId);
        PromoPlatformLink link = linkRepository.findByIdAndChatId(linkId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bog'lanish topilmadi"));
        linkRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public PromoSearchResultDTO search(Long chatId, String platformUserId) {
        if (chatId != null) {
            if (!chatRepository.existsByChatId(chatId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat ID topilmadi: " + chatId);
            }
            List<PromoPlatformLinkDTO> links = getLinks(chatId);
            return PromoSearchResultDTO.builder()
                    .searchType("chat")
                    .chatId(chatId)
                    .links(links)
                    .build();
        }
        if (platformUserId != null && !platformUserId.trim().isEmpty()) {
            String trimmed = validatePlatformUserId(platformUserId);
            List<PromoPlatformLink> links = linkRepository.findByPlatformUserIdOrderByCreatedAtDesc(trimmed);
            if (links.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Platforma foydalanuvchi ID topilmadi: " + trimmed);
            }
            List<Long> chatIds = links.stream()
                    .map(PromoPlatformLink::getChatId)
                    .distinct()
                    .collect(Collectors.toList());
            return PromoSearchResultDTO.builder()
                    .searchType("platform")
                    .platformUserId(trimmed)
                    .links(links.stream().map(this::toLinkDto).collect(Collectors.toList()))
                    .linkedChatIds(chatIds)
                    .build();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "chatId yoki platformUserId parametri kerak");
    }

    public boolean isPromoChatAllowed(Long chatId) {
        return chatId != null && chatRepository.existsByChatId(chatId);
    }

    public boolean isPromoLinkAllowed(Long chatId, String platformUserId) {
        if (chatId == null || platformUserId == null || platformUserId.isBlank()) {
            return false;
        }
        return linkRepository.existsByChatIdAndPlatformUserId(chatId, platformUserId.trim());
    }

    private void requireChat(Long chatId) {
        if (!chatRepository.existsByChatId(chatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat ID topilmadi: " + chatId);
        }
    }

    private String validatePlatformUserId(String platformUserId) {
        if (platformUserId == null || platformUserId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Platforma foydalanuvchi ID bo'sh bo'lishi mumkin emas");
        }
        String trimmed = platformUserId.trim();
        if (!trimmed.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Platforma foydalanuvchi ID faqat raqamlardan iborat bo'lishi kerak");
        }
        return trimmed;
    }

    private String validatePlatformName(String platformName) {
        if (platformName == null || platformName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kontora nomi bo'sh bo'lishi mumkin emas");
        }
        String trimmed = platformName.trim();
        if (trimmed.length() > PLATFORM_NAME_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kontora nomi " + PLATFORM_NAME_MAX_LENGTH + " belgidan oshmasligi kerak");
        }
        return trimmed;
    }

    private PromoPlatformLinkDTO toLinkDto(PromoPlatformLink link) {
        return PromoPlatformLinkDTO.builder()
                .id(link.getId())
                .chatId(link.getChatId())
                .platformUserId(link.getPlatformUserId())
                .platformName(link.getPlatformName())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
