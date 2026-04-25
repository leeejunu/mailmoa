package com.example.mailmoa.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class MailSyncConfig {

    @Bean("mailSyncExecutor")
    public Executor mailSyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
