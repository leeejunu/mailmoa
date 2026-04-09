package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.TokenRefreshResult;

public interface GmailPort {
    void trashMail(String accessToken, String externalMessageId);
    TokenRefreshResult refreshAccessToken(String refreshToken);
}
