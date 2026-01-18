package com.mail.moa.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String emailAddress;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AuthType authType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    @Column(length = 255)
    private String appPassword;

    private LocalDateTime lastSyncedAt;
    @Column(length = 20)
    private String syncStatus;


}
