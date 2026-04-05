package com.example.mailmoa.user.infrastructure.redis;

import com.example.mailmoa.user.application.port.RefreshTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisAdapter implements RefreshTokenPort {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void saveRefreshToken(String userId, String refreshToken, long expirationMs) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                expirationMs,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public String getRefreshToken(String userId) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
    }

    @Override
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }
}
