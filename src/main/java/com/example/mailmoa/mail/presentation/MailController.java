package com.example.mailmoa.mail.presentation;

import com.example.mailmoa.mail.application.service.MailSyncService;
import com.example.mailmoa.mail.application.usecase.MailUseCase;
import com.example.mailmoa.mail.presentation.dto.res.MailDetailResponse;
import com.example.mailmoa.mail.presentation.dto.res.MailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mails")
@RequiredArgsConstructor
public class MailController {

    private final MailUseCase mailUseCase;
    private final MailSyncService mailSyncService;

    @GetMapping
    public ResponseEntity<List<MailResponse>> getMails(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                mailUseCase.getMails(Long.parseLong(userId), page, size)
                        .stream()
                        .map(MailResponse::from)
                        .toList()
        );
    }

    @GetMapping("/count")
    public ResponseEntity<?> getMailCount(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(mailUseCase.getMailCount(Long.parseLong(userId)));
    }

    @PatchMapping("/{mailId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long mailId) {
        mailUseCase.markAsRead(mailId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{mailId}")
    public ResponseEntity<Void> deleteMail(@PathVariable Long mailId) {
        mailUseCase.deleteMail(mailId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mailId}")
    public ResponseEntity<MailDetailResponse> getMail(@PathVariable Long mailId) {
        return ResponseEntity.ok(MailDetailResponse.from(mailUseCase.getMail(mailId)));
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync(@AuthenticationPrincipal String userId) {
        mailSyncService.syncByUserId(Long.parseLong(userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/load-older")
    public ResponseEntity<Integer> loadOlderNaverMails(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(mailUseCase.loadOlderNaverMails(Long.parseLong(userId)));
    }
}
