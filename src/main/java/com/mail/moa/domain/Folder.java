package com.mail.moa.domain;

import jakarta.persistence.*;

@Entity
public class Folder {

    @Id
    @Column(name = "folder_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private EmailAccount emailAccount;

    private String name;
    @Column(length = 255)
    private String originalId;

    private Long parentId;
}
