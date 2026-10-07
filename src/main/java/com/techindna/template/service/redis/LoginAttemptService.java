package com.techindna.template.service.redis;

import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final String KEY_PREFIX = "auth:login:attempts:";

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long recordFailure(UUID userId) {
        return redisTemplate.opsForValue().increment(KEY_PREFIX + userId);
    }

    public void clear(UUID userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
