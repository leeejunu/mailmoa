package com.example.mailmoa.mail.application.dto;

import java.util.Map;

public record MailCountResult(long total, long unread, Map<String, Long> byProvider) {}
