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

@Slf4j
@RestController
@RequestMapping("/api/v1/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    @GetMapping
    public ResponseEntity<?> getEmails(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        String mails = null;
        try {
            mails = mailService.fetchMails(principalDetails.getUsername());
        }catch (Exception e) {
            log.info(e.getMessage());
        }

        return ResponseEntity.ok().body(mails);
    }
}
