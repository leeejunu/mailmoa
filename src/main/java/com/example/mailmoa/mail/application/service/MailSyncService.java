package com.example.mailmoa.mail.application.service;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.port.GmailWatchPort;
import com.example.mailmoa.mail.application.port.MailPushPort;
import com.example.mailmoa.mail.application.port.MailSyncPort;
import com.example.mailmoa.mail.domain.model.Mail;
import com.example.mailmoa.mail.domain.repository.MailRepository;
import com.example.mailmoa.mailaccount.application.service.MailAccountTokenService;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MailSyncService {

    private final Set<Long> syncingAccounts = ConcurrentHashMap.newKeySet();

    private final Map<MailProvider, MailSyncPort> syncPorts;
    private final MailRepository mailRepository;
    private final MailAccountRepository mailAccountRepository;
    private final MailAccountTokenService mailAccountTokenService;
    private final MailPushPort mailPushPort;
    private final GmailWatchPort gmailWatchPort;
    private final ObjectMapper objectMapper;

    @Value("${gmail.pubsub.topic-name}")
    private String pubsubTopicName;

    public MailSyncService(List<MailSyncPort> syncPorts,
                           MailRepository mailRepository,
                           MailAccountRepository mailAccountRepository,
                           MailAccountTokenService mailAccountTokenService,
                           MailPushPort mailPushPort,
                           GmailWatchPort gmailWatchPort,
                           ObjectMapper objectMapper) {
        this.syncPorts = syncPorts.stream()
                .collect(Collectors.toMap(MailSyncPort::getSupportedProvider, p -> p));
        this.mailRepository = mailRepository;
        this.mailAccountRepository = mailAccountRepository;
        this.mailAccountTokenService = mailAccountTokenService;
        this.mailPushPort = mailPushPort;
        this.gmailWatchPort = gmailWatchPort;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 300000)
    public void syncAllAccounts() {
        List<MailAccount> accounts = mailAccountRepository.findAll();
        for (MailAccount account : accounts) {
            syncMails(account);
        }
    }

    // Gmail Watch 만료 전 갱신 (7일 주기, 6일마다 갱신)
    @Scheduled(fixedDelay = 6L * 24 * 60 * 60 * 1000)
    public void renewGmailWatch() {
        mailAccountRepository.findAll().stream()
                .filter(a -> a.getProvider() == MailProvider.GMAIL)
                .forEach(account -> {
                    try {
                        String credential = mailAccountTokenService.getValidAccessToken(account);
                        gmailWatchPort.setupWatch(credential, pubsubTopicName);
                        log.info("[Watch 갱신] accountId={}", account.getId());
                    } catch (Exception e) {
                        log.warn("[Watch 갱신 실패] accountId={}: {}", account.getId(), e.getMessage());
                    }
                });
    }

    public void syncByUserId(Long userId) {
        List<MailAccount> accounts = mailAccountRepository.findAllByUserId(userId);
        for (MailAccount account : accounts) {
            syncMails(account);
        }
    }

    public void syncAccountById(Long accountId) {
        mailAccountRepository.findById(accountId).ifPresent(this::syncMails);
    }

    // Gmail Pub/Sub 웹훅에서 호출
    public void syncByEmailAddress(String emailAddress) {
        mailAccountRepository.findByEmailAddress(emailAddress)
                .ifPresent(this::syncMails);
    }

    // Gmail Pub/Sub push 알림 처리
    public void handleGmailPush(String encodedData) {
        try {
            String json = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(json);
            String emailAddress = node.path("emailAddress").asText(null);
            if (emailAddress == null) {
                log.warn("[Gmail Push] emailAddress 없음");
                return;
            }
            log.info("[Gmail Push] 수신 - emailAddress={}", emailAddress);
            syncByEmailAddress(emailAddress);
        } catch (Exception e) {
            log.warn("[Gmail Push] 처리 실패: {}", e.getMessage());
        }
    }

    public void syncMails(MailAccount account) {
        if (!syncingAccounts.add(account.getId())) {
            log.info("[동기화 스킵] 이미 진행 중 - accountId={}", account.getId());
            return;
        }
        Thread.ofVirtual()
                .name("mail-sync-" + account.getId())
                .start(() -> {
                    try {
                        doSync(account);
                    } finally {
                        syncingAccounts.remove(account.getId());
                    }
                });
    }

    private void doSync(MailAccount account) {
        MailSyncPort port = syncPorts.get(account.getProvider());
        if (port == null) {
            log.warn("지원하지 않는 메일 프로바이더: {}", account.getProvider());
            return;
        }

        boolean isFirstGmailSync = account.getLastHistoryId() == null
                && account.getProvider() == MailProvider.GMAIL;
        long totalStart = System.currentTimeMillis();

        try {
            String credential = mailAccountTokenService.getValidAccessToken(account);
            SyncResponseResult result = port.fetchMails(account, credential);

            if (result.historyId() != null) {
                account.updateLastHistoryId(result.historyId());
                mailAccountRepository.save(account);
            }

            List<Mail> saved = saveNewMails(account.getId(), result.mails());
            pushIfAny(account.getUserId(), saved);

            log.info("[동기화] accountId={}, 저장: {}개, 다음 페이지: {}, 소요: {}ms",
                    account.getId(), saved.size(), result.nextPageToken() != null,
                    System.currentTimeMillis() - totalStart);

            if (result.nextPageToken() != null) {
                List<MailSyncData> remaining = port.fetchRemaining(account, credential, result.nextPageToken());
                List<Mail> savedRemaining = saveNewMails(account.getId(), remaining);
                pushIfAny(account.getUserId(), savedRemaining);
                log.info("[백그라운드 동기화 완료] accountId={}, {}개 저장, 전체 소요: {}ms",
                        account.getId(), savedRemaining.size(), System.currentTimeMillis() - totalStart);
            }

            if (isFirstGmailSync) {
                try {
                    gmailWatchPort.setupWatch(credential, pubsubTopicName);
                    log.info("[Watch 설정] accountId={}", account.getId());
                } catch (Exception e) {
                    log.warn("[Watch 설정 실패] accountId={}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[동기화 실패] accountId={}: {}", account.getId(), e.getMessage());
        }
    }

    private List<Mail> saveNewMails(Long accountId, List<MailSyncData> mails) {
        if (mails.isEmpty()) return List.of();
        Set<String> existing = mailRepository.findExternalMessageIdsByMailAccountId(accountId);
        List<Mail> newMails = mails.stream()
                .filter(r -> !existing.contains(r.messageId()))
                .map(r -> Mail.create(accountId, r.messageId(), r.subject(), r.senderName(),
                        r.senderEmail(), r.snippet(), r.body(), r.provider(), r.receivedAt()))
                .toList();
        if (newMails.isEmpty()) return List.of();
        try {
            mailRepository.saveAll(newMails);
            return newMails;
        } catch (DataIntegrityViolationException e) {
            log.warn("[동기화] 중복 메일 저장 무시 - accountId={}", accountId);
            return List.of();
        }
    }

    private void pushIfAny(Long userId, List<Mail> saved) {
        if (saved.isEmpty()) return;
        mailPushPort.push(userId);
    }
}
