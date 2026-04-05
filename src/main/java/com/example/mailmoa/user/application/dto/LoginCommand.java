package com.example.mailmoa.user.application.dto;

public record LoginCommand(
        String email,
        String password
) {}
