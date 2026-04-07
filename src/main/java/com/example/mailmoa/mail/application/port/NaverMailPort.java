package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.SyncResponseResult;

import com.example.mailmoa.mail.application.dto.MailSyncData;

import java.util.List;

public interface NaverMailPort {
    SyncResponseResult fetchMails(String email, String password, String lastUid);
    List<MailSyncData> fetchOlderMails(String email, String password, long beforeUid, int limit);
    String fetchMailBody(String email, String password, String uid);
    void trashMail(String email, String password, String uid);
    void testConnection(String email, String password);
}
