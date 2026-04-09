package com.example.mailmoa.mail.presentation.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailPushRequest(Message message, String subscription) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String data, String messageId, String publishTime) {}
}
