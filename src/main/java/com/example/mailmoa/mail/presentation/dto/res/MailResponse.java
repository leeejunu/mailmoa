package com.example.mailmoa.mail.presentation.dto.res;

import com.example.mailmoa.mail.application.dto.MailResult;

import java.time.LocalDateTime;

public record MailResponse(
        Long id,
        String subject,
        String senderName,
        String senderEmail,
        String snippet,
        LocalDateTime receivedAt,
        String provider,
        boolean read
) {
    public static MailResponse from(MailResult result) {
        return new MailResponse(
                result.id(),
                result.subject(),
                result.senderName(),
                result.senderEmail(),
                result.snippet(),
                result.receivedAt(),
                result.provider(),
                result.read()
        );
    }
}
