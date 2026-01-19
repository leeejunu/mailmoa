package com.mail.moa.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String userEmail;

//    @Column
//    private String password;

    private String name;

    private User(String userEmail, String name) {
        this.userEmail = userEmail;
//        this.password = password;
        this.name = name;
    }

    public static User createUser(String userEmail, String name) {
        return new User(userEmail, name);
    }
}
