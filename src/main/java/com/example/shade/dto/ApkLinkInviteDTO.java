package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApkLinkInviteDTO {
    private Long id;
    private String name;
    private String inviteLink;
    private String type;
    private Integer sortOrder;
}
