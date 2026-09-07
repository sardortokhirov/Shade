package com.example.shade.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(json.contains("\"url\":\"https://t.me/misterpays\""), json);
        assertEquals(7, markup.getKeyboard().size());
        assertEquals("TOPUP", markup.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("WALLET", markup.getKeyboard().get(1).get(0).getCallbackData());
        assertEquals("WITHDRAW", markup.getKeyboard().get(2).get(0).getCallbackData());
        assertEquals("BONUS", markup.getKeyboard().get(3).get(0).getCallbackData());
        assertEquals("BOZOR", markup.getKeyboard().get(4).get(0).getCallbackData());
        assertEquals("CONTACT", markup.getKeyboard().get(5).get(0).getCallbackData());
        assertEquals("https://t.me/misterpays", markup.getKeyboard().get(6).get(0).getUrl());
        for (int i = 0; i < 7; i++) {
            assertEquals(1, markup.getKeyboard().get(i).size());
        }
    }
}
