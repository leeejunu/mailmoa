package com.example.mailmoa.user.presentation.dto.res;

import com.example.mailmoa.user.application.dto.LoginResult;

public record LoginResponse(
        String accessToken
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken());
    }
}
