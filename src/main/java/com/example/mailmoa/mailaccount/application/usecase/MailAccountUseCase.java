package com.example.mailmoa.mailaccount.application.usecase;

import com.example.mailmoa.mailaccount.application.dto.ConnectNaverCommand;
import com.example.mailmoa.mailaccount.application.dto.SaveMailAccountCommand;

public interface MailAccountUseCase {
    Long saveMailAccount(SaveMailAccountCommand command);
    Long connectNaver(ConnectNaverCommand command);
}
