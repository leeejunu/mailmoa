package com.example.mailmoa.mail.domain;

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
    private String body;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private boolean isStarred = false;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(nullable = false)
    private LocalDateTime syncedAt;
}
