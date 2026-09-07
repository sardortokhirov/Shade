package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apk_link_user_preference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkUserPreference {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;
}
