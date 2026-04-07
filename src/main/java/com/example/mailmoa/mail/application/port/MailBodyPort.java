package com.example.mailmoa.mail.application.port;

import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;

public interface MailBodyPort {
    MailProvider getSupportedProvider();
    String fetchMailBody(MailAccount account, String credential, String externalMessageId);
}
