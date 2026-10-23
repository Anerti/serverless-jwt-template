package com.techindna.template.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.exception.ErrorBody;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.rate-limit.critical-per-minute=2",
            "app.rate-limit.sensitive-per-minute=2",
            "app.rate-limit.standard-per-minute=3"
        })
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
@RequiredArgsConstructor
class RateLimitTest extends TestcontainersConfig {

    private static final String RATE_LIMIT_KEY_PATTERN = "ratelimit:*";

    private final TestRestTemplate restTemplate;
    private final StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        clean();
    }

    @AfterEach
    void clean() {
        Set<String> keys = redis.keys(RATE_LIMIT_KEY_PATTERN);
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void criticalRouteOverLimitIsRejectedWithRetryAfter() {
        assertThat(login(null).getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login(null).getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        ResponseEntity<ErrorBody> response = login(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(429);
        assertThat(response.getBody().getError()).isEqualTo("TOO MANY REQUESTS");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Too many requests. Please try again later.");
        assertThat(Long.parseLong(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .isPositive();
    }

    @Test
    void sensitiveRouteHasItsOwnLimit() {
        login(null);
        login(null);
        assertThat(login(null).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        String path = "/users/" + UUID.randomUUID();
        assertThat(exchange(HttpMethod.DELETE, path)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange(HttpMethod.DELETE, path)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange(HttpMethod.DELETE, path)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void standardRouteAllowsMoreRequestsThanCriticalOnes() {
        String path = "/users/" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertThat(exchange(HttpMethod.GET, path)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(exchange(HttpMethod.GET, path)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void healthCheckIsNotRateLimited() {
        for (int i = 0; i < 5; i++) {
            assertThat(exchange(HttpMethod.GET, "/syn")).isEqualTo(HttpStatus.OK);
        }
        assertThat(redis.keys(RATE_LIMIT_KEY_PATTERN)).isEmpty();
    }

    @Test
    void rateLimitBucketExpiresInRedis() {
        login(null);

        Set<String> keys = redis.keys(RATE_LIMIT_KEY_PATTERN);
        assertThat(keys).hasSize(1);
        assertThat(keys.iterator().next()).startsWith("ratelimit:critical:ip:");
        assertThat(redis.getExpire(keys.iterator().next())).isPositive();
    }

    @Test
    void forwardedForFromTrustedProxyIsUsedAsClientIp() {
        login("203.0.113.9");

        assertThat(redis.keys(RATE_LIMIT_KEY_PATTERN))
                .containsExactly("ratelimit:critical:ip:203.0.113.9");
    }

    private ResponseEntity<ErrorBody> login(String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwardedFor != null) {
            headers.set("X-Forwarded-For", forwardedFor);
        }
        return restTemplate.postForEntity(
                "/auth/login", new HttpEntity<>("{}", headers), ErrorBody.class);
    }

    private HttpStatus exchange(HttpMethod method, String path) {
        return HttpStatus.valueOf(
                restTemplate.exchange(path, method, HttpEntity.EMPTY, String.class)
                        .getStatusCode()
                        .value());
    }
}
