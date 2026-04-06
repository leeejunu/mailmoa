package com.example.mailmoa.mail.application.service;

import com.example.mailmoa.mail.application.dto.MailCountResult;
import com.example.mailmoa.mail.application.dto.MailDetailResult;
import com.example.mailmoa.mail.application.dto.MailResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import com.example.mailmoa.mail.application.usecase.MailUseCase;
import com.example.mailmoa.mail.domain.model.Mail;
import com.example.mailmoa.mail.domain.repository.MailRepository;
import com.example.mailmoa.mailaccount.application.service.MailAccountTokenService;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailService implements MailUseCase {

    private final MailAccountRepository mailAccountRepository;
    private final MailRepository mailRepository;
    private final GmailPort gmailPort;
    private final MailAccountTokenService mailAccountTokenService;

    @Override
    public List<MailResult> getMails(Long userId, int page, int size) {
        List<Long> accountIds = mailAccountRepository.findAllByUserId(userId).stream()
                .map(account -> account.getId())
                .toList();
        return mailRepository.findByMailAccountIdIn(accountIds, PageRequest.of(page, size)).stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    public MailCountResult getMailCount(Long userId) {
        List<Long> accountIds = mailAccountRepository.findAllByUserId(userId).stream()
                .map(account -> account.getId())
                .toList();
        Map<String, Long> byProvider = mailRepository.countByProviderIn(accountIds);
        long total = byProvider.values().stream().mapToLong(Long::longValue).sum();
        long unread = mailRepository.countUnreadByMailAccountIdIn(accountIds);
        return new MailCountResult(total, unread, byProvider);
    }

    @Override
    @Transactional
    public void markAsRead(Long mailId) {
        Mail mail = mailRepository.findById(mailId)
                .orElseThrow(() -> new IllegalArgumentException("Mail not found: " + mailId));
        mail.markAsRead();
        mailRepository.save(mail);
    }

    @Override
    @Transactional
    public void deleteMail(Long mailId) {
        Mail mail = mailRepository.findById(mailId)
                .orElseThrow(() -> new IllegalArgumentException("Mail not found: " + mailId));
        MailAccount account = mailAccountRepository.findById(mail.getMailAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        String accessToken = mailAccountTokenService.getValidAccessToken(account);
        gmailPort.trashMail(accessToken, mail.getExternalMessageId());
        mailRepository.deleteById(mailId);
    }

    @Override
    public MailDetailResult getMail(Long mailId) {
        Mail mail = mailRepository.findById(mailId)
                .orElseThrow(() -> new IllegalArgumentException("Mail not found: " + mailId));
        return new MailDetailResult(
                mail.getId(),
                mail.getSubject(),
                mail.getSenderName(),
                mail.getSenderEmail(),
                mail.getSnippet(),
                mail.getBody(),
                mail.getReceivedAt(),
                mail.getProvider()
        );
    }

    private MailResult toResult(Mail mail) {
        return new MailResult(
                mail.getId(),
                mail.getSubject(),
                mail.getSenderName(),
                mail.getSenderEmail(),
                mail.getSnippet(),
                mail.getReceivedAt(),
                mail.getProvider(),
                mail.isRead()
        );
    }
}
