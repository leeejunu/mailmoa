package com.example.mailmoa.mailaccount.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class MailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MailProvider provider;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private LocalDateTime tokenExpiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static MailAccount create(Long userId, String emailAddress, MailProvider provider,
                                     String accessToken, String refreshToken, LocalDateTime tokenExpiresAt) {
        MailAccount account = new MailAccount();
        account.userId = userId;
        account.emailAddress = emailAddress;
        account.provider = provider;
        account.accessToken = accessToken;
        account.refreshToken = refreshToken;
        account.tokenExpiresAt = tokenExpiresAt;
        return account;
    }

    public void updateTokens(String accessToken, String refreshToken, LocalDateTime tokenExpiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
