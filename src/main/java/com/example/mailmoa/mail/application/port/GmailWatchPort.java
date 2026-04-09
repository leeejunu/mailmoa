package com.example.mailmoa.mail.application.port;

import reactor.core.publisher.Mono;

public interface GmailWatchPort {
    Mono<Void> setupWatch(String accessToken, String topicName);
}
