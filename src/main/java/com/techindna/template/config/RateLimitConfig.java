package com.techindna.template.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> rateLimitBuckets(LettuceConnectionFactory connectionFactory) {
        RedisClient client = (RedisClient) connectionFactory.getRequiredNativeClient();
        return Bucket4jLettuce.casBasedBuilder(
                        client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)))
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofSeconds(10)))
                .build();
    }

    public static BucketConfiguration perMinute(long requests) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(requests).refillGreedy(requests, Duration.ofMinutes(1)))
                .build();
    }
}
