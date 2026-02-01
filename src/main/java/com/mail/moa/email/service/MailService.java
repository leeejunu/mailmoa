package com.mail.moa.email.service;

import java.util.Map;

public interface MailService {

    Map<String, Object> fetchMails(String email) throws Exception;
}
