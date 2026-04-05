package com.example.mailmoa.user.presentation.dto.req;

public record SignUpRequest(
        String email,
        String password,
        String name
) {}
