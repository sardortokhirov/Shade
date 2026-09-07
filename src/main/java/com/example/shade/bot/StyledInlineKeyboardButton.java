package com.example.shade.bot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

/**
 * Extends the library button with Telegram Bot API {@code style}
 * (success=green, danger=red, primary=blue). Library 6.x does not model this field yet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StyledInlineKeyboardButton extends InlineKeyboardButton {

    public static final String STYLE_SUCCESS = "success";
    public static final String STYLE_DANGER = "danger";
    public static final String STYLE_PRIMARY = "primary";

    @JsonProperty("style")
    private String style;

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public static InlineKeyboardButton callback(String text, String callbackData) {
        return callback(text, callbackData, null);
    }

    public static InlineKeyboardButton callback(String text, String callbackData, String style) {
        StyledInlineKeyboardButton button = new StyledInlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        if (style != null && !style.isBlank()) {
            button.setStyle(style);
        }
        return button;
    }

    public static InlineKeyboardButton url(String text, String url, String style) {
        StyledInlineKeyboardButton button = new StyledInlineKeyboardButton();
        button.setText(text);
        button.setUrl(url);
        if (style != null && !style.isBlank()) {
            button.setStyle(style);
        }
        return button;
    }
}
