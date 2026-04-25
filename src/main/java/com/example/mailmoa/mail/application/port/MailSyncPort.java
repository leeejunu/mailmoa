package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;

import java.util.List;

public interface MailSyncPort {
    MailProvider getSupportedProvider();
    SyncResponseResult fetchMails(MailAccount account, String credential);
    List<MailSyncData> fetchRemaining(MailAccount account, String credential, String continuationToken);
}
