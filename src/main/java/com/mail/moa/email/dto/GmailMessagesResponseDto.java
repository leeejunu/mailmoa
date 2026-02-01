package com.mail.moa.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GmailMessagesResponseDto {
    private List<MessageIdDto> messages;

    @Getter @Setter
    public static class MessageIdDto {
        private String id;
    }
}
