package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserFilter {
    private Boolean blocked;
    private String language;
    private Boolean hasBalance;
    private Long searchChatId;
    private String searchPhone;
}
