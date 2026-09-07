package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apk_link_invite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkInvite {

    public static final String TYPE_CHANNEL = "CHANNEL";
    public static final String TYPE_GROUP = "GROUP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "invite_link", nullable = false, length = 1024)
    private String inviteLink;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
