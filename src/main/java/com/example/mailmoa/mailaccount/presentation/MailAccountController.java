package com.example.mailmoa.mailaccount.presentation;

import com.example.mailmoa.mail.application.service.MailSyncService;
import com.example.mailmoa.mailaccount.application.dto.ConnectNaverCommand;
import com.example.mailmoa.mailaccount.application.usecase.MailAccountUseCase;
import com.example.mailmoa.mailaccount.presentation.dto.req.ConnectNaverRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/mailaccounts")
@RequiredArgsConstructor
public class MailAccountController {

    private final MailAccountUseCase mailAccountUseCase;
    private final MailSyncService mailSyncService;

    @PostMapping("/naver")
    public ResponseEntity<Void> connectNaver(
            @AuthenticationPrincipal String userId,
            @RequestBody ConnectNaverRequest request) {
        Long accountId = mailAccountUseCase.connectNaver(
                new ConnectNaverCommand(Long.parseLong(userId), request.email(), request.password()));
        CompletableFuture.runAsync(() -> mailSyncService.syncAccountById(accountId));
        return ResponseEntity.ok().build();
    }
}
