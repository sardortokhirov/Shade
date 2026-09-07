package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoChatSummaryDTO {
    private Long chatId;
    private long linkCount;
    private boolean filled;
}
