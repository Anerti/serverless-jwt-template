package com.techindna.template.service.redis;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:";

    private final ProxyManager<String> buckets;

    public ConsumptionProbe tryConsume(String key, BucketConfiguration configuration) {
        return buckets.builder()
                .build(KEY_PREFIX + key, () -> configuration)
                .tryConsumeAndReturnRemaining(1);
    }
}
