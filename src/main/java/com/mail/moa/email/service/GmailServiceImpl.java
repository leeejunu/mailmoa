package com.mail.moa.email.service;

import com.mail.moa.domain.EmailAccount;
import com.mail.moa.domain.User;
import com.mail.moa.emailAccount.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class GmailServiceImpl implements MailService{

    private final EmailAccountRepository emailAccountRepository;

    @Override
    public String fetchMails(String email) throws Exception {
        EmailAccount emailAccount = emailAccountRepository.findByEmailAddress(email)
                .orElseThrow(() -> new IllegalStateException("계정 없음"));

        RestClient restClient = RestClient.create();

        return restClient.get()
                .uri("https://www.googleapis.com/gmail/v1/users/me/labels")
                .header("Authorization", "Bearer " + emailAccount.getAccessToken())
                .retrieve()
                .body(String.class);
    }
}
