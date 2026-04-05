package com.example.mailmoa.user.application.dto;

import com.example.mailmoa.user.domain.model.User;

import java.util.UUID;

public record SignUpResult(
        UUID id,
        String email,
        String name
) {
    public static SignUpResult from(User user) {
        return new SignUpResult(user.getId(), user.getEmail(), user.getName());
    }
}
