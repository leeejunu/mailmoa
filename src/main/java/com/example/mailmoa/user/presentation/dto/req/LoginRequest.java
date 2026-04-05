package com.example.mailmoa.user.presentation.dto.req;

public record LoginRequest(
        String email,
        String password
) {}
