package com.mail.moa.emailAccount;

import com.mail.moa.domain.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByEmailAddress(String emailAddress);

    List<EmailAccount> findAllByEmailAddress(String emailAddress);
}
