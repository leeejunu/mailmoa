package com.example.mailmoa.mailaccount.domain.repository;

import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;

import java.util.List;
import java.util.Optional;

public interface MailAccountRepository {
    MailAccount save(MailAccount mailAccount);
    Optional<MailAccount> findByUserIdAndProvider(Long userId, MailProvider provider);
    List<MailAccount> findAllByUserId(Long userId);
}
