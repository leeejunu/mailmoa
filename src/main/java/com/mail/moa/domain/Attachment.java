package com.mail.moa.domain;

import jakarta.persistence.*;

@Entity
public class Attachment {

    @Id
    @Column(name = "attachment_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JoinColumn(name = "email_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Email email;

    @Column(length = 255, nullable = false)
    private String fileName;

    private Long fileSize;

    @Column(length = 100)
    private String contentType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String s3Url;
}
