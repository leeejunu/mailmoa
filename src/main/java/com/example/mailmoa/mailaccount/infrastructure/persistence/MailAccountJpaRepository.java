package com.example.mailmoa.mailaccount.infrastructure.persistence;

import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailAccountJpaRepository extends JpaRepository<MailAccount, Long> {
    Optional<MailAccount> findByUserIdAndProvider(Long userId, MailProvider provider);
    Optional<MailAccount> findByUserIdAndEmailAddress(Long userId, String emailAddress);
    Optional<MailAccount> findByEmailAddress(String emailAddress);
    List<MailAccount> findAllByUserId(Long userId);
}
