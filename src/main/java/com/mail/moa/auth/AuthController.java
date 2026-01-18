package com.mail.moa.auth;

import com.mail.moa.auth.dto.SignupRequestDto;
import com.mail.moa.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/join")
    public ResponseEntity<?> join(SignupRequestDto signupRequestDto) {
        authService.join(signupRequestDto);
        return ResponseEntity.ok().body("회원가입 성공");
    }
}
