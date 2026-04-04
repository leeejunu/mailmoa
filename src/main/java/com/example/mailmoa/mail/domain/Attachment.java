package com.example.mailmoa.mail.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mailId;

    @Column(nullable = false)
    private String fileName;

    private Long fileSize;

    @Column(nullable = false, length = 500)
    private String fileUrl;
}
