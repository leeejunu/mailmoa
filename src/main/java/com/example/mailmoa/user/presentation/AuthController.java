package com.example.mailmoa.user.presentation;

import com.example.mailmoa.user.application.dto.LoginCommand;
import com.example.mailmoa.user.application.dto.SignUpCommand;
import com.example.mailmoa.user.application.usecase.UserUseCase;
import com.example.mailmoa.user.presentation.dto.req.LoginRequest;
import com.example.mailmoa.user.presentation.dto.req.SignUpRequest;
import com.example.mailmoa.user.presentation.dto.res.LoginResponse;
import com.example.mailmoa.user.presentation.dto.res.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserUseCase userUseCase;

    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@RequestBody SignUpRequest request) {
        return ResponseEntity.ok(
                SignUpResponse.from(userUseCase.signUp(
                        new SignUpCommand(request.email(), request.password(), request.name())
                ))
        );
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                LoginResponse.from(userUseCase.login(
                        new LoginCommand(request.email(), request.password())
                ))
        );
    }
}
