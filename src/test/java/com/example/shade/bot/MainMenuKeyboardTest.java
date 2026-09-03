package com.example.shade.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuKeyboardTest {

    @Test
    void serializesTelegramButtonStyles() throws Exception {
        InlineKeyboardMarkup markup = MainMenuKeyboard.build(
                "Topup", "Withdraw", "Wallet", "Bonus", "Bozor", "Contact", "Guide");
        String json = new ObjectMapper().writeValueAsString(markup);
        assertTrue(json.contains("\"style\":\"success\""), json);
        assertTrue(json.contains("\"style\":\"danger\""), json);
        assertTrue(json.contains("\"style\":\"primary\""), json);
        assertTrue(json.contains("\"callback_data\":\"TOPUP\""), json);
        assertTrue(json.contains("\"callback_data\":\"BOZOR\""), json);
        assertTrue(json.contains("\"callback_data\":\"CONTACT\""), json);
        assertTrue(json.contains("\"url\":\"https://t.me/BaronPeyInfo\""), json);
    }
}
