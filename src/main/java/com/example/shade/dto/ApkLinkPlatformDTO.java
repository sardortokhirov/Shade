package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApkLinkPlatformDTO {
    private Long id;
    private String name;
    private String linkUrl;
    private String apkFileId;
    private String apkUrl;
    private Integer sortOrder;
    private String apkFileName;
    private String apkCaption;
    private String linkKeyword;
    private String apkKeyword;
}
