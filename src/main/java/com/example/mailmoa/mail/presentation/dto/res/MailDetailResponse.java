package com.example.mailmoa.mail.presentation.dto.res;

import com.example.mailmoa.mail.application.dto.MailDetailResult;

import java.time.LocalDateTime;

public record MailDetailResponse(
        Long id,
        String subject,
        String senderName,
        String senderEmail,
        String snippet,
        String body,
        LocalDateTime receivedAt,
        String provider
) {
    public static MailDetailResponse from(MailDetailResult result) {
        return new MailDetailResponse(
                result.id(),
                result.subject(),
                result.senderName(),
                result.senderEmail(),
                result.snippet(),
                result.body(),
                result.receivedAt(),
                result.provider()
        );
    }
}
