package com.example.mailmoa.mail.application.service;

import com.example.mailmoa.mail.application.exception.MailNotFoundException;
import com.example.mailmoa.mail.application.dto.MailCountResult;
import com.example.mailmoa.mail.application.dto.MailDetailResult;
import com.example.mailmoa.mail.application.dto.MailResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import com.example.mailmoa.mail.application.port.MailBodyPort;
import com.example.mailmoa.mail.application.port.NaverMailPort;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
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
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MailService implements MailUseCase {

    private final MailAccountRepository mailAccountRepository;
    private final MailRepository mailRepository;
    private final Map<MailProvider, MailBodyPort> bodyPorts;
    private final GmailPort gmailPort;
    private final NaverMailPort naverMailPort;
    private final MailAccountTokenService mailAccountTokenService;

    public MailService(MailAccountRepository mailAccountRepository,
                       MailRepository mailRepository,
                       List<MailBodyPort> bodyPorts,
                       GmailPort gmailPort,
                       NaverMailPort naverMailPort,
                       MailAccountTokenService mailAccountTokenService) {
        this.mailAccountRepository = mailAccountRepository;
        this.mailRepository = mailRepository;
        this.bodyPorts = bodyPorts.stream()
                .collect(Collectors.toMap(MailBodyPort::getSupportedProvider, p -> p));
        this.gmailPort = gmailPort;
        this.naverMailPort = naverMailPort;
        this.mailAccountTokenService = mailAccountTokenService;
    }

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
                .orElseThrow(() -> new MailNotFoundException(mailId));
        mail.markAsRead();
        mailRepository.save(mail);
    }

    @Override
    @Transactional
    public void deleteMail(Long mailId) {
        Mail mail = mailRepository.findById(mailId)
                .orElseThrow(() -> new MailNotFoundException(mailId));
        MailAccount account = mailAccountRepository.findById(mail.getMailAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        String credential = mailAccountTokenService.getValidAccessToken(account);
        if (account.getProvider() == MailProvider.GMAIL) {
            gmailPort.trashMail(credential, mail.getExternalMessageId());
        } else if (account.getProvider() == MailProvider.NAVER) {
            naverMailPort.trashMail(account.getEmailAddress(), credential, mail.getExternalMessageId());
        }
        mailRepository.deleteById(mailId);
    }

    @Override
    @Transactional
    public MailDetailResult getMail(Long mailId) {
        Mail mail = mailRepository.findById(mailId)
                .orElseThrow(() -> new MailNotFoundException(mailId));

        if (mail.getBody() == null || mail.getBody().isEmpty()) {
            MailAccount account = mailAccountRepository.findById(mail.getMailAccountId()).orElse(null);
            if (account != null) {
                MailBodyPort port = bodyPorts.get(account.getProvider());
                if (port != null) {
                    String credential = mailAccountTokenService.getValidAccessToken(account);
                    mail.updateBody(port.fetchMailBody(account, credential, mail.getExternalMessageId()));
                    mailRepository.save(mail);
                }
            }
        }

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
