package com.example.mailmoa.mail.application.dto;

import java.time.LocalDateTime;

public record MailDetailResult(
        Long id,
        String subject,
        String senderName,
        String senderEmail,
        String snippet,
        String body,
        LocalDateTime receivedAt,
        String provider
) {}
