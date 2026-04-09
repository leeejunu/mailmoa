package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MailSyncPort {
    MailProvider getSupportedProvider();
    Mono<SyncResponseResult> fetchMails(MailAccount account, String credential);
    Flux<MailSyncData> fetchRemaining(MailAccount account, String credential, String continuationToken);
}
