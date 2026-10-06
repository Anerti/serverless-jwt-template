package com.techindna.template.service.redis;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VerificationTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:verification:";

    private final StringRedisTemplate redisTemplate;

    public VerificationTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String createForUser(UUID userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate
                .opsForValue()
                .set(KEY_PREFIX + token, userId.toString(), TOKEN_TTL);
        return token;
    }

    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
