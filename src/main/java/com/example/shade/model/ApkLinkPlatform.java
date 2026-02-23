package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apk_link_platform")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "link_url", nullable = false, length = 1024)
    private String linkUrl;

    @Column(name = "apk_file_id", length = 512)
    private String apkFileId;

    @Column(name = "apk_url", length = 1024)
    private String apkUrl;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
