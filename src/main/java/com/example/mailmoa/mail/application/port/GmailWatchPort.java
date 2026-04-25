package com.example.mailmoa.mail.application.port;

public interface GmailWatchPort {
    void setupWatch(String accessToken, String topicName);
}
