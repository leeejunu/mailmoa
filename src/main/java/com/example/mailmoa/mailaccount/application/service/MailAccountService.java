package com.example.mailmoa.mailaccount.application.service;

import com.example.mailmoa.global.util.AesEncryptor;
import com.example.mailmoa.mailaccount.application.dto.SaveMailAccountCommand;
import com.example.mailmoa.mailaccount.application.usecase.MailAccountUseCase;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MailAccountService implements MailAccountUseCase {

    private final MailAccountRepository mailAccountRepository;
    private final AesEncryptor aesEncryptor;

    @Override
    @Transactional
    public void saveMailAccount(SaveMailAccountCommand command) {
        String encryptedAccessToken = aesEncryptor.encrypt(command.accessToken());
        String encryptedRefreshToken = command.refreshToken() != null
                ? aesEncryptor.encrypt(command.refreshToken())
                : null;

        mailAccountRepository.findByUserIdAndProvider(command.userId(), command.provider())
                .ifPresentOrElse(
                        mailAccount -> updateTokens(mailAccount, encryptedAccessToken, encryptedRefreshToken, command),
                        () -> createMailAccount(command, encryptedAccessToken, encryptedRefreshToken)
                );
    }

    private void updateTokens(MailAccount mailAccount, String accessToken, String refreshToken, SaveMailAccountCommand command) {
        mailAccount.updateTokens(accessToken, refreshToken, command.tokenExpiresAt());
    }

    private void createMailAccount(SaveMailAccountCommand command, String accessToken, String refreshToken) {
        mailAccountRepository.save(MailAccount.create(
                command.userId(),
                command.emailAddress(),
                command.provider(),
                accessToken,
                refreshToken,
                command.tokenExpiresAt()
        ));
    }
}
