package com.mail.moa.email.service;

import java.util.List;
import java.util.Map;

public interface MailService {

    List<Map<String, Object>> fetchMails(String email) throws Exception;
}
