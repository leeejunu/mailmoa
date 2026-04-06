package com.example.mailmoa.mailaccount.infrastructure.persistence;

import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import com.example.mailmoa.mailaccount.domain.repository.MailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MailAccountRepositoryAdapter implements MailAccountRepository {

    private final MailAccountJpaRepository mailAccountJpaRepository;

    @Override
    public MailAccount save(MailAccount mailAccount) {
        return mailAccountJpaRepository.save(mailAccount);
    }

    @Override
    public Optional<MailAccount> findById(Long id) {
        return mailAccountJpaRepository.findById(id);
    }

    @Override
    public Optional<MailAccount> findByUserIdAndProvider(Long userId, MailProvider provider) {
        return mailAccountJpaRepository.findByUserIdAndProvider(userId, provider);
    }

    @Override
    public List<MailAccount> findAllByUserId(Long userId) {
        return mailAccountJpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<MailAccount> findAll() {
        return mailAccountJpaRepository.findAll();
    }
}
