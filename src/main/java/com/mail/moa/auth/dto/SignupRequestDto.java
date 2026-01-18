package com.mail.moa.auth.dto;

public record SignupRequestDto(
        String userEmail,
        String password,
        String name) {
}
