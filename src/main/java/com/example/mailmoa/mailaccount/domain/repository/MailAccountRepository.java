package com.example.mailmoa.mailaccount.domain.repository;

import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;

import java.util.List;
import java.util.Optional;

public interface MailAccountRepository {
    MailAccount save(MailAccount mailAccount);
    Optional<MailAccount> findById(Long id);
    Optional<MailAccount> findByUserIdAndProvider(Long userId, MailProvider provider);
    Optional<MailAccount> findByEmailAddress(String emailAddress);
    List<MailAccount> findAllByUserId(Long userId);
    List<MailAccount> findAll();
}
