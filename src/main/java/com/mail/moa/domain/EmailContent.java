package com.mail.moa.domain;

import jakarta.persistence.*;

@Entity
public class EmailContent {

    @Id
    private Long id;

    @MapsId
    @JoinColumn(name = "email_id")
    @OneToOne(fetch = FetchType.LAZY)
    private Email email;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String bodyHtml;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String bodyText;

}
