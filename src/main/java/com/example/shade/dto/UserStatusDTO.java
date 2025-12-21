package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserStatusDTO {
    private Long chatId;
    private String language;
    private boolean blocked;
    private String phoneNumber;
}
