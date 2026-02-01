package com.mail.moa.email;

import com.mail.moa.email.service.MailService;
import com.mail.moa.security.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    @GetMapping
    public ResponseEntity<?> fetchMails(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        try {
            log.info("email: {}", principalDetails.getUsername());
            List<Map<String, Object>> mails = mailService.fetchMails(principalDetails.getUsername());
            return ResponseEntity.ok(mails);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
