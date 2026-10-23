package com.techindna.template.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
@RequiredArgsConstructor
class CorsTest extends TestcontainersConfig {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    private final TestRestTemplate restTemplate;

    @Test
    void preflightFromAllowedOriginIsAccepted() {
        ResponseEntity<String> response = preflight(ALLOWED_ORIGIN, "PATCH");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    void preflightFromUnknownOriginIsRejected() {
        ResponseEntity<String> response = preflight("https://evil.example", "PATCH");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    void retryAfterIsExposedToBrowsers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ALLOWED_ORIGIN);

        ResponseEntity<String> response =
                restTemplate.exchange("/syn", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getAccessControlExposeHeaders()).contains(HttpHeaders.RETRY_AFTER);
    }

    private ResponseEntity<String> preflight(String origin, String method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.valueOf(method));
        headers.setAccessControlRequestHeaders(List.of("Authorization", "Content-Type"));
        return restTemplate.exchange(
                "/users/00000000-0000-0000-0000-000000000000",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class);
    }
}
