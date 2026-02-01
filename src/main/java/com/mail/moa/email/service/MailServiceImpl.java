package com.mail.moa.email.service;

import com.mail.moa.domain.EmailAccount;
import com.mail.moa.email.service.provider.MailProvider;
import com.mail.moa.emailAccount.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MailServiceImpl implements MailService{

    private final List<MailProvider> providers;
    private final EmailAccountRepository emailAccountRepository;

    @Override
    public List<Map<String, Object>> fetchMails(String email) throws Exception {
        List<EmailAccount> accounts = emailAccountRepository.findAllByEmailAddress(email);

        return accounts.parallelStream().flatMap((account) -> {
            MailProvider provider = providers.stream()
                    .filter(p -> p.supports(account.getProvider()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("지원하지 않는 서비스입니다."));
            return provider.fetchMails(account.getEmailAddress()).stream();
        }).toList();
    }
}
