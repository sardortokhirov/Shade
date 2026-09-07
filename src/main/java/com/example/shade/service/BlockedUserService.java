package com.example.shade.service;

import com.example.shade.model.BlockedPhoneNumber;
import com.example.shade.model.BlockedUser;
import com.example.shade.repository.BlockedPhoneNumberRepository;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.util.PhoneNormalization;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockedUserService {
    private static final Logger logger = LoggerFactory.getLogger(BlockedUserService.class);
    private static final String BLOCKED_SENTINEL = "BLOCKED";

    private final BlockedUserRepository blockedUserRepository;
    private final BlockedPhoneNumberRepository blockedPhoneNumberRepository;

    public enum BlockChatResult {
        SUCCESS,
        ALREADY_BLOCKED
    }

    public enum UnblockChatResult {
        SUCCESS,
        NOT_BLOCKED
    }

    @Transactional
    public BlockChatResult blockChat(Long chatId) {
        BlockedUser user = blockedUserRepository.findById(chatId)
                .orElseGet(() -> BlockedUser.builder().chatId(chatId).build());
        if (BLOCKED_SENTINEL.equals(user.getPhoneNumber())) {
            return BlockChatResult.ALREADY_BLOCKED;
        }
        String pn = user.getPhoneNumber();
        if (pn != null && !pn.isBlank() && !BLOCKED_SENTINEL.equals(pn)) {
            String norm = PhoneNormalization.normalize(pn);
            if (norm != null) {
                BlockedPhoneNumber row = blockedPhoneNumberRepository.findById(norm)
                        .orElseGet(() -> BlockedPhoneNumber.builder()
                                .normalizedPhone(norm)
                                .build());
                row.setLinkedChatId(chatId);
                blockedPhoneNumberRepository.save(row);
                logger.info("Phone {} added/updated on blocklist (linked chatId={})", norm, chatId);
            }
        }
        user.setPhoneNumber(BLOCKED_SENTINEL);
        blockedUserRepository.save(user);
        return BlockChatResult.SUCCESS;
    }

    @Transactional
    public UnblockChatResult unblockChat(Long chatId) {
        blockedPhoneNumberRepository.deleteByLinkedChatId(chatId);
        BlockedUser user = blockedUserRepository.findById(chatId).orElse(null);
        if (user == null || !BLOCKED_SENTINEL.equals(user.getPhoneNumber())) {
            return UnblockChatResult.NOT_BLOCKED;
        }
        blockedUserRepository.deleteById(chatId);
        logger.info("Unblocked chatId {} (BlockedUser row removed)", chatId);
        return UnblockChatResult.SUCCESS;
    }

    @Transactional
    public boolean blockPhoneNumber(String rawPhone) {
        String norm = PhoneNormalization.normalize(rawPhone);
        if (norm == null) {
            return false;
        }
        BlockedPhoneNumber row = blockedPhoneNumberRepository.findById(norm)
                .orElseGet(() -> BlockedPhoneNumber.builder()
                        .normalizedPhone(norm)
                        .build());
        row.setLinkedChatId(null);
        blockedPhoneNumberRepository.save(row);
        logger.info("Phone {} blocked by number (no linked chat)", norm);
        return true;
    }

    /**
     * Removes blocklist rows tied to this chat (e.g. before hard-deleting the user).
     */
    @Transactional
    public void removePhoneLinksForChat(Long chatId) {
        blockedPhoneNumberRepository.deleteByLinkedChatId(chatId);
    }

    @Transactional
    public boolean unblockPhoneNumber(String rawPhone) {
        String norm = PhoneNormalization.normalize(rawPhone);
        if (norm == null) {
            return false;
        }
        if (!blockedPhoneNumberRepository.existsById(norm)) {
            return false;
        }
        blockedPhoneNumberRepository.deleteById(norm);
        logger.info("Phone {} removed from blocklist", norm);
        return true;
    }
}
