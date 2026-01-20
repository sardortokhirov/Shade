package com.example.shade.service;

import com.example.shade.bot.MessageSender;
import com.example.shade.model.BlockedUser;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.BlockedUserRepository;
import com.example.shade.repository.UserBalanceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContactService {
    private static final Logger logger = LoggerFactory.getLogger(ContactService.class);
    private final MessageSender messageSender;
    private final LanguageSessionService languageSessionService;
    private final BlockedUserRepository blockedUserRepository;
    private final UserBalanceRepository userBalanceRepository;

    public void handleContact(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(languageSessionService.getTranslation(chatId, "contact.message.contact_prompt"));
        message.setReplyMarkup(createContactKeyboard(chatId));
        messageSender.sendMessage(message, chatId);
    }

    private InlineKeyboardMarkup createContactKeyboard(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1 - Admin button
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "contact.button.admin"), "https://t.me/Boss9w")));

        // 2 - Chat button
        rows.add(List.of(createButton(languageSessionService.getTranslation(chatId, "contact.button.chat"), "https://t.me/Abadiy_Kassa")));
        rows.add(createNavigationButtons(chatId));
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> createNavigationButtons(Long chatId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "contact.button.back"), "BACK"));
        buttons.add(createButton(languageSessionService.getTranslation(chatId, "contact.button.home"), "HOME"));
        return buttons;
    }

    private InlineKeyboardButton createButton(String text, String callbackOrUrl) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        if (callbackOrUrl.startsWith("http")) {
            button.setUrl(callbackOrUrl);
        } else {
            button.setCallbackData(callbackOrUrl);
        }
        return button;
    }

    /**
     * Handles phone number update in a transactional manner to ensure UserBalance is preserved.
     * This method ensures that:
     * 1. BlockedUser is properly retrieved or created
     * 2. UserBalance is never overwritten if it already exists
     * 3. All operations are atomic within a transaction
     * 
     * @param chatId The user's chat ID
     * @param phoneNumber The phone number to save (will be normalized with + prefix)
     * @return true if UserBalance was created (new user), false if it already existed
     */
    @Transactional
    public boolean handlePhoneNumberUpdate(Long chatId, String phoneNumber) {
        logger.info("Handling phone number update for chatId: {}", chatId);
        
        // Normalize phone number
        if (phoneNumber != null && !phoneNumber.startsWith("+")) {
            phoneNumber = "+" + phoneNumber;
        }
        
        // Safely retrieve or create BlockedUser
        BlockedUser blockedUser = blockedUserRepository.findById(chatId)
                .orElse(BlockedUser.builder().chatId(chatId).build());
        
        String oldPhoneNumber = blockedUser.getPhoneNumber();
        
        // Update phone number
        blockedUser.setPhoneNumber(phoneNumber);
        blockedUserRepository.save(blockedUser);
        
        logger.info("BlockedUser updated for chatId {}: {} -> {}", chatId, oldPhoneNumber, phoneNumber);
        
        // Safely check and create UserBalance if it doesn't exist
        // Use double-check pattern to prevent race condition overwrites
        Optional<UserBalance> existingBalance = userBalanceRepository.findById(chatId);
        boolean balanceCreated = false;
        
        if (existingBalance.isPresent()) {
            // Balance exists - preserve it
            UserBalance balance = existingBalance.get();
            logger.info("UserBalance already exists for chatId {}: tickets={}, balance={} - PRESERVED", 
                    chatId, balance.getTickets(), balance.getBalance());
        } else {
            // Double-check to prevent race condition overwrites
            if (userBalanceRepository.existsById(chatId)) {
                // Entity exists but wasn't found - possible race condition
                // Don't create a new one to avoid overwriting existing data
                logger.warn("UserBalance exists but wasn't found by findById for chatId {} - NOT creating new one", chatId);
            } else {
                // Truly doesn't exist - safe to create
                UserBalance newBalance = UserBalance.builder()
                        .chatId(chatId)
                        .tickets(0L)
                        .balance(BigDecimal.ZERO)
                        .build();
                userBalanceRepository.save(newBalance);
                balanceCreated = true;
                logger.info("Created new UserBalance for chatId {}: tickets=0, balance=0", chatId);
            }
        }
        
        return balanceCreated;
    }
}