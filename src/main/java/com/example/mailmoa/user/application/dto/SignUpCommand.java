package com.example.mailmoa.user.application.dto;

public record SignUpCommand(
        String email,
        String password,
        String name
) {}
