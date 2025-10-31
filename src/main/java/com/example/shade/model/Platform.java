package com.example.shade.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "platforms")
public class Platform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, unique = true)
    private String apiKey;

    // A "common" platform will have a null secret.
    @Column(unique = true, nullable = true)
    private String secret;

    // This field will differentiate the types in the database.
    @Column(nullable = false)
    private String type; // Replaces isMostbet for better extensibility

    // A "mostbet" platform will have a null login.
    @Column(unique = true, nullable = true)
    private String login;

    // A "mostbet" platform will have a null password.
    @Column(nullable = true)
    private String password;

    // A "mostbet" platform will have a null workplaceId.
    @Column(unique = true, nullable = true, name = "workplace_id")
    private String workplaceId;
}