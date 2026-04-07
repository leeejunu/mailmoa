package com.example.mailmoa.mail.infrastructure.persistence;

import com.example.mailmoa.mail.domain.model.Mail;
import com.example.mailmoa.mail.domain.repository.MailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MailRepositoryAdapter implements MailRepository {

    private final MailJpaRepository mailJpaRepository;

    @Override
    public void saveAll(List<Mail> mails) {
        mailJpaRepository.saveAll(mails);
    }

    @Override
    public List<Mail> findAllByMailAccountId(Long mailAccountId) {
        return mailJpaRepository.findAllByMailAccountId(mailAccountId);
    }

    @Override
    public List<Mail> findByMailAccountIdIn(List<Long> accountIds, Pageable pageable) {
        return mailJpaRepository.findByMailAccountIdIn(accountIds, pageable);
    }

    @Override
    public Set<String> findExternalMessageIdsByMailAccountId(Long mailAccountId) {
        return mailJpaRepository.findExternalMessageIdsByMailAccountId(mailAccountId);
    }

    @Override
    public Optional<Mail> findById(Long id) {
        return mailJpaRepository.findById(id);
    }

    @Override
    public void save(Mail mail) {
        mailJpaRepository.save(mail);
    }

    @Override
    public void deleteById(Long id) {
        mailJpaRepository.deleteById(id);
    }

    @Override
    public Optional<String> findOldestExternalMessageIdByMailAccountId(Long mailAccountId) {
        List<String> result = mailJpaRepository
                .findExternalMessageIdsByMailAccountIdOrderByReceivedAt(mailAccountId, PageRequest.of(0, 1));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Map<String, Long> countByProviderIn(List<Long> accountIds) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : mailJpaRepository.countByProviderIn(accountIds)) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    @Override
    public long countUnreadByMailAccountIdIn(List<Long> accountIds) {
        return mailJpaRepository.countUnreadByMailAccountIdIn(accountIds);
    }
}
