package com.mail.moa.email.service.provider;

import java.util.List;
import java.util.Map;

public interface MailProvider {

    boolean supports(String provider);

    List<Map<String, Object>> fetchMails(String email);
}
