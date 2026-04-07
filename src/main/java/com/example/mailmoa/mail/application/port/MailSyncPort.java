package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;

public interface MailSyncPort {
    MailProvider getSupportedProvider();
    SyncResponseResult fetchMails(MailAccount account, String credential);
}
