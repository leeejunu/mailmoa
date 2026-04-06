package com.example.mailmoa.mail.infrastructure.persistence;

import com.example.mailmoa.mail.domain.Mail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface MailJpaRepository extends JpaRepository<Mail, Long> {
    List<Mail> findAllByMailAccountId(Long mailAccountId);

    @Query("SELECT m FROM Mail m WHERE m.mailAccountId IN :accountIds ORDER BY m.receivedAt DESC")
    List<Mail> findByMailAccountIdIn(List<Long> accountIds, Pageable pageable);

    @Query("SELECT m.externalMessageId FROM Mail m WHERE m.mailAccountId = :mailAccountId")
    Set<String> findExternalMessageIdsByMailAccountId(Long mailAccountId);

    @Query("SELECT m.provider, COUNT(m) FROM Mail m WHERE m.mailAccountId IN :accountIds GROUP BY m.provider")
    List<Object[]> countByProviderIn(List<Long> accountIds);

    @Query("SELECT COUNT(m) FROM Mail m WHERE m.mailAccountId IN :accountIds AND m.isRead = false")
    long countUnreadByMailAccountIdIn(List<Long> accountIds);
}
