package com.example.mailmoa.mailaccount.infrastructure.redis;

import com.example.mailmoa.mailaccount.application.port.OAuthStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OAuthStateRedisAdapter implements OAuthStatePort {

    private static final String STATE_PREFIX = "oauth:state:";
    private static final long STATE_TTL_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void saveState(String state, String userId) {
        redisTemplate.opsForValue().set(STATE_PREFIX + state, userId, STATE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public String getUserId(String state) {
        return redisTemplate.opsForValue().get(STATE_PREFIX + state);
    }

    @Override
    public void deleteState(String state) {
        redisTemplate.delete(STATE_PREFIX + state);
    }
}
