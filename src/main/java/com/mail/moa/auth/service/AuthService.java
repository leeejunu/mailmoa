package com.mail.moa.auth.service;

import com.mail.moa.auth.dto.SigninRequestDto;
import com.mail.moa.auth.dto.SignupRequestDto;
import com.nimbusds.oauth2.sdk.TokenResponse;

public interface AuthService {
    void join(SignupRequestDto signupRequestDto);

    String login(SigninRequestDto signinRequestDto);
}
