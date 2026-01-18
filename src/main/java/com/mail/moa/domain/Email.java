package com.mail.moa.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Email {

    @Id
    @Column(name = "email_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private EmailAccount emailAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(length = 255, nullable = false)
    private String messageId;

    private String senderName;

    private String senderEmail;

    @Column(length = 500)
    private String subject;

    @Column(length = 1000)
    private String snippet;

    private boolean isRead;

    private boolean isImportant;

    private LocalDateTime receivedAt;
}
