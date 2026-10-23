package com.techindna.template.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=192.0.2.1/32")
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
@RequiredArgsConstructor
class ClientIpSpoofingTest extends TestcontainersConfig {

    private static final String SPOOFED_IP = "203.0.113.9";
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
    void forwardingHeadersFromUntrustedPeerAreIgnored() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", SPOOFED_IP);
        headers.set("X-Real-IP", SPOOFED_IP);
        headers.set("CF-Connecting-IP", SPOOFED_IP);
        headers.set("True-Client-IP", SPOOFED_IP);

        restTemplate.postForEntity("/auth/login", new HttpEntity<>("{}", headers), String.class);

        Set<String> keys = redis.keys(RATE_LIMIT_KEY_PATTERN);
        assertThat(keys).hasSize(1);
        assertThat(keys.iterator().next())
                .startsWith("ratelimit:critical:ip:")
                .doesNotContain(SPOOFED_IP);
    }
}
