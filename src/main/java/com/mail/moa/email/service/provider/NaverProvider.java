package com.mail.moa.email.service.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NaverProvider implements MailProvider{
    @Override
    public boolean supports(String provider) {
        return "NAVER".equalsIgnoreCase(provider);
    }

    @Override
    public List<Map<String, Object>> fetchMails(String email) {
        return List.of();
    }
}
