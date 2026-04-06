package com.example.mailmoa.mailaccount.application.service;

import com.example.mailmoa.global.util.AesEncryptor;
import com.example.mailmoa.mail.application.dto.TokenRefreshResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MailAccountTokenService {

    private final GmailPort gmailPort;
    private final AesEncryptor aesEncryptor;
    private final MailAccountRepository mailAccountRepository;

    @Transactional
    public String getValidAccessToken(MailAccount account) {
        if (account.getTokenExpiresAt() != null &&
                account.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return aesEncryptor.decrypt(account.getAccessToken());
        }

        String refreshToken = aesEncryptor.decrypt(account.getRefreshToken());
        TokenRefreshResult result = gmailPort.refreshAccessToken(refreshToken);
        LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(result.expiresIn());

        account.updateTokens(
                aesEncryptor.encrypt(result.accessToken()),
                account.getRefreshToken(),
                newExpiresAt
        );
        mailAccountRepository.save(account);

        return result.accessToken();
    }
}
