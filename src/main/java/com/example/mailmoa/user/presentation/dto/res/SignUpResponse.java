package com.example.mailmoa.user.presentation.dto.res;

import com.example.mailmoa.user.application.dto.SignUpResult;

import java.util.UUID;

public record SignUpResponse(
        Long id,
        String email,
        String name
) {
    public static SignUpResponse from(SignUpResult result) {
        return new SignUpResponse(result.id(), result.email(), result.name());
    }
}
