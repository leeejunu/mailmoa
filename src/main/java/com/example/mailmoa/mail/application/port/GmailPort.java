package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.SyncResponseResult;

public interface GmailPort {
    SyncResponseResult fetchMails(String accessToken, String lastHistoryId);
}
