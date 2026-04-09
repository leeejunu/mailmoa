package com.example.mailmoa.mail.application.exception;

public class MailNotFoundException extends RuntimeException {
    public MailNotFoundException(Long mailId) {
        super("Mail not found: " + mailId);
    }
}
