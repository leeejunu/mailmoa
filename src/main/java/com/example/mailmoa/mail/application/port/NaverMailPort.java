package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.MailSyncData;

import java.util.List;

public interface NaverMailPort {
    List<MailSyncData> fetchRange(String email, String password, int from, int to);
    String fetchMailBody(String email, String password, String uid);
    void trashMail(String email, String password, String uid);
    void testConnection(String email, String password);
}
