package com.techindna.template.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.auth.VerificationResponse;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mail.EmailService;
import com.techindna.template.service.redis.VerificationTokenService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class AuthVerificationTest extends TestcontainersConfig {

    private static final String USERNAME = "verification-user";
    private static final String EMAIL = "verification.user@example.com";
    private static final String VERIFICATION_KEY_PREFIX = "auth:verification:";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redis;

    @MockitoBean private EmailService emailService;

    AuthVerificationTest(
            TestRestTemplate restTemplate,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            VerificationTokenService verificationTokenService,
            JwtTokenProvider jwtTokenProvider,
            StringRedisTemplate redis) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationTokenService = verificationTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redis = redis;
    }

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        Set<String> keys = redis.keys(VERIFICATION_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void postVerificationConsumesTokenVerifiesUserAndReturnsJwt() {
        JUser user = saveUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId());

        ResponseEntity<VerificationResponse> response = verify(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().user().id()).isEqualTo(user.getId());
        assertThat(response.getBody().user().username()).isEqualTo(USERNAME);
        assertThat(response.getBody().user().email()).isEqualTo(EMAIL);
        assertThat(response.getBody().user().role())
                .isEqualTo(UserRole.CUSTOMER.name().toLowerCase());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerified()).isTrue();
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();

        var claims = jwtTokenProvider.validateToken(response.getBody().token());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(UserRole.CUSTOMER.name());
        assertThat(claims.get("ip_address", String.class)).isNotBlank();
    }

    @Test
    void getVerificationPageDoesNotConsumeTokenOrVerifyUser() {
        JUser user = saveUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId());

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/auth/verify/" + token, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MediaType.TEXT_HTML.isCompatibleWith(response.getHeaders().getContentType()))
                .isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody())
                .contains("<form")
                .contains("method=\"post\"")
                .contains("/auth/verify/" + token)
                .contains("Confirm");
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token))
                .isEqualTo(user.getId().toString());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerified()).isFalse();
    }

    @Test
    void invalidOrConsumedVerificationTokenReturnsUnauthorized() {
        ResponseEntity<String> invalidResponse = verifyError("00000000-0000-0000-0000-000000000000");
        assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalidResponse.getBody()).contains("Invalid or expired token");

        JUser user = saveUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId());
        assertThat(verify(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> replayResponse = verifyError(token);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replayResponse.getBody()).contains("Invalid or expired token");
    }

    @Test
    void malformedUserIdStoredForTokenReturnsUnauthorizedAndConsumesToken() {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(VERIFICATION_KEY_PREFIX + token, "not-a-uuid");

        ResponseEntity<String> response = verifyError(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or expired token");
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token)).isNull();
    }

    @Test
    void tokenForNonexistentUserReturnsUnauthorizedAndIsConsumed() {
        String token = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(VERIFICATION_KEY_PREFIX + token, UUID.randomUUID().toString());

        ResponseEntity<String> response = verifyError(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or expired token");
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token)).isNull();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void lockedAccountCannotCompleteVerification() {
        JUser user = saveUser(true, UserStatus.LOCKED);
        String token = verificationTokenService.createForUser(user.getId());

        ResponseEntity<String> response = verifyError(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Account locked.");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.LOCKED);
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
    }

    private ResponseEntity<VerificationResponse> verify(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.exchange(
                "/auth/verify/" + token,
                HttpMethod.POST,
                new HttpEntity<>("", headers),
                VerificationResponse.class);
    }

    private ResponseEntity<String> verifyError(String token) {
        return restTemplate.exchange(
                "/auth/verify/" + token, HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }

    private JUser saveUser(boolean verified, UserStatus status) {
        return userRepository.saveAndFlush(
                JUser.builder()
                        .username(USERNAME)
                        .password(passwordEncoder.encode("StrongPassword1!"))
                        .firstName("Verify")
                        .lastName("User")
                        .email(EMAIL)
                        .verified(verified)
                        .role(UserRole.CUSTOMER)
                        .status(status)
                        .build());
    }
}
