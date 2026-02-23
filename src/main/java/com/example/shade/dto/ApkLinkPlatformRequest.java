package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApkLinkPlatformRequest {
    private String name;
    private String linkUrl;
    private String apkFileId;
    private String apkUrl;
    private Integer sortOrder;
}
