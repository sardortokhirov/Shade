package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apk_link_keyword")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(nullable = false, length = 255)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", insertable = false, updatable = false)
    private ApkLinkPlatform platform;
}
