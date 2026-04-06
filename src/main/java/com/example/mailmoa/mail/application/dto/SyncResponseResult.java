package com.example.mailmoa.mail.application.dto;

import java.util.List;

public record SyncResponseResult(List<MailSyncData> mails, String historyId) {}
