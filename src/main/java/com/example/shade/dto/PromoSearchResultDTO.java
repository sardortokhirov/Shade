package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoSearchResultDTO {
    /** "chat" or "platform" */
    private String searchType;
    private Long chatId;
    private String platformUserId;
    private List<PromoPlatformLinkDTO> links;
    private List<Long> linkedChatIds;
}
