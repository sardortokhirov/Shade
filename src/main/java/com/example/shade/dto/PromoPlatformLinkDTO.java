package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoPlatformLinkDTO {
    private Long id;
    private Long chatId;
    private String platformUserId;
    private String platformName;
    private LocalDateTime createdAt;
}
