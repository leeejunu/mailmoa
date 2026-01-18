package com.mail.moa.auth.service;

import com.mail.moa.auth.dto.SignupRequestDto;

public interface AuthService {
    void join(SignupRequestDto signupRequestDto);
}
