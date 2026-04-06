package com.example.mailmoa.mail.application.usecase;

import com.example.mailmoa.mail.application.dto.MailCountResult;
import com.example.mailmoa.mail.application.dto.MailDetailResult;
import com.example.mailmoa.mail.application.dto.MailResult;

import java.util.List;

public interface MailUseCase {
    List<MailResult> getMails(Long userId, int page, int size);
    MailDetailResult getMail(Long mailId);
    MailCountResult getMailCount(Long userId);
    void markAsRead(Long mailId);
    void deleteMail(Long mailId);
}
