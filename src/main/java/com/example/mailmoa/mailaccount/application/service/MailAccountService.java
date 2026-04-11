package com.example.mailmoa.mailaccount.application.service;

import com.example.mailmoa.global.util.AesEncryptor;
import com.example.mailmoa.mail.application.port.NaverMailPort;
import com.example.mailmoa.mailaccount.application.dto.ConnectNaverCommand;
import com.example.mailmoa.mailaccount.application.dto.SaveMailAccountCommand;
import com.example.mailmoa.mailaccount.application.usecase.MailAccountUseCase;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MailAccountService implements MailAccountUseCase {

    private final MailAccountRepository mailAccountRepository;
    private final AesEncryptor aesEncryptor;
    private final NaverMailPort naverMailPort;

    @Override
    @Transactional
    public Long saveMailAccount(SaveMailAccountCommand command) {
        String encryptedAccessToken = aesEncryptor.encrypt(command.accessToken());
        String encryptedRefreshToken = command.refreshToken() != null
                ? aesEncryptor.encrypt(command.refreshToken())
                : null;

        mailAccountRepository.findByUserIdAndEmailAddress(command.userId(), command.emailAddress())
                .ifPresentOrElse(
                        mailAccount -> updateTokens(mailAccount, encryptedAccessToken, encryptedRefreshToken, command),
                        () -> createMailAccount(command, encryptedAccessToken, encryptedRefreshToken)
                );

        return mailAccountRepository.findByUserIdAndEmailAddress(command.userId(), command.emailAddress())
                .orElseThrow().getId();
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

    @Override
    @Transactional
    public Long connectNaver(ConnectNaverCommand command) {
        naverMailPort.testConnection(command.email(), command.password());
        saveMailAccount(new SaveMailAccountCommand(
                command.userId(),
                command.email(),
                MailProvider.NAVER,
                command.password(),
                null,
                null
        ));
        return mailAccountRepository.findByUserIdAndProvider(command.userId(), MailProvider.NAVER)
                .orElseThrow().getId();
    }
}
