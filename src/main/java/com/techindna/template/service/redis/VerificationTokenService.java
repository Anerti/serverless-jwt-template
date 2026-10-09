package com.techindna.template.service.redis;

import java.time.Duration;
import java.util.UUID;

import com.techindna.template.service.enums.VerificationFlow;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VerificationTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:verification:";
    private static final String VALUE_SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;

    public VerificationTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String createForUser(UUID userId, VerificationFlow flow) {
        String token = UUID.randomUUID().toString();
        redisTemplate
                .opsForValue()
                .set(
                        KEY_PREFIX + token,
                        flow.name() + VALUE_SEPARATOR + userId,
                        TOKEN_TTL);
        return token;
    }

    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    public VerificationToken consume(String token) {
        return parse(redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token));
    }

    public VerificationFlow flowOf(String token) {
        VerificationToken verificationToken =
                parse(redisTemplate.opsForValue().get(KEY_PREFIX + token));
        return verificationToken == null ? null : verificationToken.flow();
    }

    private VerificationToken parse(String value) {
        if (value == null) {
            return null;
        }

        int separator = value.indexOf(VALUE_SEPARATOR);
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }

        try {
            VerificationFlow flow = VerificationFlow.valueOf(value.substring(0, separator));
            UUID userId = UUID.fromString(value.substring(separator + 1));
            return new VerificationToken(flow, userId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record VerificationToken(VerificationFlow flow, UUID userId) {}
}
