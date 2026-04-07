package com.example.mailmoa.mail.application.service;

import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.port.MailSyncPort;
import com.example.mailmoa.mail.domain.model.Mail;
import com.example.mailmoa.mail.domain.repository.MailRepository;
import com.example.mailmoa.mailaccount.application.service.MailAccountTokenService;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class MailSyncService {

    private final Map<MailProvider, MailSyncPort> syncPorts;
    private final MailRepository mailRepository;
    private final MailAccountRepository mailAccountRepository;
    private final MailAccountTokenService mailAccountTokenService;

    public MailSyncService(List<MailSyncPort> syncPorts,
                           MailRepository mailRepository,
                           MailAccountRepository mailAccountRepository,
                           MailAccountTokenService mailAccountTokenService) {
        this.syncPorts = syncPorts.stream()
                .collect(Collectors.toMap(MailSyncPort::getSupportedProvider, p -> p));
        this.mailRepository = mailRepository;
        this.mailAccountRepository = mailAccountRepository;
        this.mailAccountTokenService = mailAccountTokenService;
    }

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

    public void syncByUserId(Long userId) {
        List<MailAccount> accounts = mailAccountRepository.findAllByUserId(userId);
        for (MailAccount account : accounts) {
            try {
                syncMails(account);
            } catch (Exception e) {
                log.warn("메일 동기화 실패 - accountId: {}, error: {}", account.getId(), e.getMessage());
            }
        }
    }

    public void syncAccountById(Long accountId) {
        mailAccountRepository.findById(accountId).ifPresent(account -> {
            try {
                syncMails(account);
            } catch (Exception e) {
                log.warn("메일 동기화 실패 - accountId: {}, error: {}", accountId, e.getMessage());
            }
        });
    }

    public void syncMails(MailAccount account) {
        MailSyncPort port = syncPorts.get(account.getProvider());
        if (port == null) {
            log.warn("지원하지 않는 메일 프로바이더: {}", account.getProvider());
            return;
        }

        String credential = mailAccountTokenService.getValidAccessToken(account);
        SyncResponseResult result = port.fetchMails(account, credential);

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

        log.info("[동기화] accountId={}, 가져온 메일: {}개, 신규 저장: {}개",
                account.getId(), result.mails().size(), newMails.size());

        if (!newMails.isEmpty()) {
            mailRepository.saveAll(newMails);
        }

        if (result.historyId() != null) {
            account.updateLastHistoryId(result.historyId());
            mailAccountRepository.save(account);
        }
    }
}
