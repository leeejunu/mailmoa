package com.example.mailmoa.mail.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Mail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mailAccountId;

    @Column(nullable = false)
    private String externalMessageId;

    @Column(length = 500)
    private String subject;

    private String senderName;

    @Column(nullable = false)
    private String senderEmail;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private boolean isStarred = false;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    public void markAsRead() {
        this.isRead = true;
    }

    public void updateBody(String body) {
        this.body = body;
    }

    public static Mail create(Long mailAccountId, String externalMessageId, String subject,
                              String senderName, String senderEmail, String snippet,
                              String body, String provider, LocalDateTime receivedAt) {
        Mail mail = new Mail();
        mail.mailAccountId = mailAccountId;
        mail.externalMessageId = externalMessageId;
        mail.subject = subject;
        mail.senderName = senderName;
        mail.senderEmail = senderEmail;
        mail.snippet = snippet;
        mail.body = body;
        mail.provider = provider;
        mail.receivedAt = receivedAt;
        mail.syncedAt = LocalDateTime.now();
        return mail;
    }
}
