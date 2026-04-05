package com.example.mailmoa.user.application.usecase;

import com.example.mailmoa.user.application.dto.LoginCommand;
import com.example.mailmoa.user.application.dto.LoginResult;
import com.example.mailmoa.user.application.dto.SignUpCommand;
import com.example.mailmoa.user.application.dto.SignUpResult;

public interface UserUseCase {
    SignUpResult signUp(SignUpCommand command);
    LoginResult login(LoginCommand command);
}
