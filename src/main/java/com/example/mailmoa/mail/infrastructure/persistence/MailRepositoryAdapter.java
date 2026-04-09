package com.example.mailmoa.mail.infrastructure.persistence;

import com.example.mailmoa.mail.domain.model.Mail;
import com.example.mailmoa.mail.domain.repository.MailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MailRepositoryAdapter implements MailRepository {

    private final MailJpaRepository mailJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAll(List<Mail> mails) {
        if (mails.isEmpty()) return;
        StringBuilder sql = new StringBuilder(
                "INSERT INTO mail (mail_account_id, external_message_id, subject, sender_name, sender_email, " +
                "snippet, body, provider, is_read, is_starred, received_at, synced_at) VALUES ");
        List<Object> params = new ArrayList<>(mails.size() * 12);
        for (int i = 0; i < mails.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(?,?,?,?,?,?,?,?,?,?,?,?)");
            Mail m = mails.get(i);
            params.add(m.getMailAccountId());
            params.add(m.getExternalMessageId());
            params.add(m.getSubject());
            params.add(m.getSenderName());
            params.add(m.getSenderEmail());
            params.add(m.getSnippet());
            params.add(m.getBody());
            params.add(m.getProvider());
            params.add(m.isRead());
            params.add(m.isStarred());
            params.add(Timestamp.valueOf(m.getReceivedAt()));
            params.add(Timestamp.valueOf(m.getSyncedAt()));
        }
        jdbcTemplate.update(sql.toString(), params.toArray());
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
