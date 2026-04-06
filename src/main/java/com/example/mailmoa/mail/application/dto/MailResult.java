package com.example.mailmoa.mail.application.dto;

import java.time.LocalDateTime;

public record MailResult(
        Long id,
        String subject,
        String senderName,
        String senderEmail,
        String snippet,
        LocalDateTime receivedAt,
        String provider,
        boolean read
) {}
