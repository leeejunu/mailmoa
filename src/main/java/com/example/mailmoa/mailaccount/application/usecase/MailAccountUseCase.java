package com.example.mailmoa.mailaccount.application.usecase;

import com.example.mailmoa.mailaccount.application.dto.SaveMailAccountCommand;

public interface MailAccountUseCase {
    void saveMailAccount(SaveMailAccountCommand command);
}
