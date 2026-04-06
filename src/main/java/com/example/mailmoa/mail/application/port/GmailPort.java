package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.dto.TokenRefreshResult;

public interface GmailPort {
    SyncResponseResult fetchMails(String accessToken, String lastHistoryId);
    void trashMail(String accessToken, String externalMessageId);
    TokenRefreshResult refreshAccessToken(String refreshToken);
}
