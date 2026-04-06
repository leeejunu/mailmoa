package com.example.mailmoa.mail.domain.repository;

import com.example.mailmoa.mail.domain.model.Mail;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MailRepository {
    void saveAll(List<Mail> mails);
    List<Mail> findAllByMailAccountId(Long mailAccountId);
    List<Mail> findByMailAccountIdIn(List<Long> accountIds, Pageable pageable);
    Set<String> findExternalMessageIdsByMailAccountId(Long mailAccountId);
    Optional<Mail> findById(Long id);
    void save(Mail mail);
    void deleteById(Long id);
    Map<String, Long> countByProviderIn(List<Long> accountIds);
    long countUnreadByMailAccountIdIn(List<Long> accountIds);
}
