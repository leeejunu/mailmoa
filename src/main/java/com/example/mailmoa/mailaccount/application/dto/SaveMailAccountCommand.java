package com.example.mailmoa.mailaccount.application.dto;

import com.example.mailmoa.mailaccount.domain.model.MailProvider;

import java.time.LocalDateTime;

public record SaveMailAccountCommand(
        Long userId,
        String emailAddress,
        MailProvider provider,
        String accessToken,
        String refreshToken,
        LocalDateTime tokenExpiresAt
) {}
