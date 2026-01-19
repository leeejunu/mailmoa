package com.mail.moa.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
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

    public static EmailAccount createEmailAccount(String email, String accessToken, String provider, User user) {
        EmailAccount account = new EmailAccount();
        account.emailAddress = email;
        account.accessToken = accessToken;
        account.provider = provider; // "google"
        account.user = user;
        account.authType = AuthType.OAUTH2; // OAuth2 방식임을 명시
        account.syncStatus = "CONNECTED";
        return account;
    }
}
