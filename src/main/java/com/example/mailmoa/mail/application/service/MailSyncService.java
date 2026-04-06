package com.example.mailmoa.mail.application.service;

import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import com.example.mailmoa.mail.domain.model.Mail;
import com.example.mailmoa.mail.domain.repository.MailRepository;
import com.example.mailmoa.mailaccount.application.service.MailAccountTokenService;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailSyncService {

    private final MailAccountRepository mailAccountRepository;
    private final MailRepository mailRepository;
    private final GmailPort gmailPort;
    private final MailAccountTokenService mailAccountTokenService;

    @Scheduled(fixedDelay = 300000) // 5분마다
    public void syncAllAccounts() {
        List<MailAccount> accounts = mailAccountRepository.findAll();
        for (MailAccount account : accounts) {
            try {
                syncMails(account);
            } catch (Exception e) {
                log.warn("메일 동기화 실패 - accountId: {}, error: {}", account.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void syncMails(MailAccount account) {
        if (account.getProvider() != MailProvider.GMAIL) return;

        String accessToken = mailAccountTokenService.getValidAccessToken(account);
        SyncResponseResult result = gmailPort.fetchMails(accessToken, account.getLastHistoryId());

        Set<String> existing = mailRepository.findExternalMessageIdsByMailAccountId(account.getId());

        List<Mail> newMails = result.mails().stream()
                .filter(r -> !existing.contains(r.messageId()))
                .map(r -> Mail.create(
                        account.getId(),
                        r.messageId(),
                        r.subject(),
                        r.senderName(),
                        r.senderEmail(),
                        r.snippet(),
                        r.body(),
                        r.provider(),
                        r.receivedAt()
                ))
                .toList();

        if (!newMails.isEmpty()) {
            mailRepository.saveAll(newMails);
        }

        if (result.historyId() != null) {
            account.updateLastHistoryId(result.historyId());
            mailAccountRepository.save(account);
        }
    }
}
