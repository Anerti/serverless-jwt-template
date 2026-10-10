package com.techindna.template.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.auth.VerificationResponse;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mail.EmailService;
import com.techindna.template.service.enums.VerificationFlow;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class AuthMfaConfirmationTest extends TestcontainersConfig {

    private static final String USERNAME = "verification-user";
    private static final String EMAIL = "verification.user@example.com";
    private static final String VERIFICATION_KEY_PREFIX = "auth:verification:";
    private static final String REGISTER_CONFIRM_PATH = "/auth/mfa/confirm/register/";
    private static final String LOGIN_CONFIRM_PATH = "/auth/mfa/confirm/login/";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redis;

    @MockitoBean private EmailService emailService;

    AuthMfaConfirmationTest(
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
        deleteRedisKeys(VERIFICATION_KEY_PREFIX + "*");
    }

    @Test
    void confirmRegistrationConsumesTokenVerifiesUserAndReturnsJwt() {
        JUser user = saveUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.REGISTER);

        ResponseEntity<VerificationResponse> response = confirm(REGISTER_CONFIRM_PATH + token);

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
    void confirmLoginConsumesTokenAndReturnsJwtForVerifiedUser() {
        JUser user = saveUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        ResponseEntity<VerificationResponse> response = confirm(LOGIN_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().user().id()).isEqualTo(user.getId());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerified()).isTrue();
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();

        var claims = jwtTokenProvider.validateToken(response.getBody().token());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(UserRole.CUSTOMER.name());
        assertThat(claims.get("ip_address", String.class)).isNotBlank();
    }

    @Test
    void confirmLoginForUnverifiedUserIsForbidden() {
        JUser user = saveUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        ResponseEntity<String> response = confirmError(LOGIN_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Verify your email address before signing in.");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerified()).isFalse();
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void registrationConfirmationRejectsLoginToken() {
        JUser user = saveUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        ResponseEntity<String> response = confirmError(REGISTER_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(INVALID_TOKEN_MESSAGE);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerified()).isFalse();
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token)).isNull();
    }

    @Test
    void loginConfirmationRejectsRegistrationToken() {
        JUser user = saveUser(true, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.REGISTER);

        ResponseEntity<String> response = confirmError(LOGIN_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(INVALID_TOKEN_MESSAGE);
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token)).isNull();
    }

    @Test
    void confirmationPageTargetsConfirmationEndpointForFlow() {
        JUser registerUser = saveUser(false, UserStatus.ACTIVE);
        String registerToken =
                verificationTokenService.createForUser(registerUser.getId(), VerificationFlow.REGISTER);
        String loginToken =
                verificationTokenService.createForUser(registerUser.getId(), VerificationFlow.LOGIN);

        ResponseEntity<String> registerPage = confirmationPage(registerToken);
        assertThat(registerPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(MediaType.TEXT_HTML.isCompatibleWith(registerPage.getHeaders().getContentType()))
                .isTrue();
        assertThat(registerPage.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(registerPage.getBody())
                .contains("<form")
                .contains("method=\"post\"")
                .contains(REGISTER_CONFIRM_PATH + registerToken)
                .contains("Confirm");

        ResponseEntity<String> loginPage = confirmationPage(loginToken);
        assertThat(loginPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginPage.getBody()).contains(LOGIN_CONFIRM_PATH + loginToken);

        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + registerToken))
                .isEqualTo("REGISTER:" + registerUser.getId());
        assertThat(userRepository.findById(registerUser.getId()).orElseThrow().getVerified())
                .isFalse();
    }

    @Test
    void replayedConfirmationTokenReturnsUnauthorized() {
        JUser user = saveUser(false, UserStatus.ACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.REGISTER);

        assertThat(confirm(REGISTER_CONFIRM_PATH + token).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> replayResponse = confirmError(REGISTER_CONFIRM_PATH + token);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replayResponse.getBody()).contains(INVALID_TOKEN_MESSAGE);
    }

    @Test
    void invalidConfirmationTokenReturnsUnauthorized() {
        ResponseEntity<String> response =
                confirmError(REGISTER_CONFIRM_PATH + "00000000-0000-0000-0000-000000000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(INVALID_TOKEN_MESSAGE);
    }

    @Test
    void malformedTokenValueReturnsUnauthorizedAndConsumesToken() {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(VERIFICATION_KEY_PREFIX + token, "not-a-valid-value");

        ResponseEntity<String> response = confirmError(REGISTER_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(INVALID_TOKEN_MESSAGE);
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token)).isNull();
    }

    @Test
    void tokenForNonexistentUserReturnsUnauthorizedAndIsConsumed() {
        String token = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(
                        VERIFICATION_KEY_PREFIX + token,
                        VerificationFlow.REGISTER.name() + ":" + UUID.randomUUID());

        ResponseEntity<String> response = confirmError(REGISTER_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains(INVALID_TOKEN_MESSAGE);
        assertThat(redis.opsForValue().get(VERIFICATION_KEY_PREFIX + token)).isNull();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void lockedAccountCannotCompleteRegistrationConfirmation() {
        JUser user = saveUser(true, UserStatus.LOCKED);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.REGISTER);

        ResponseEntity<String> response = confirmError(REGISTER_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .contains(
                        "Account is locked or inactive. Please request account access restoration to continue.");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.LOCKED);
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void inactiveAccountCannotCompleteLoginConfirmation() {
        JUser user = saveUser(true, UserStatus.INACTIVE);
        String token = verificationTokenService.createForUser(user.getId(), VerificationFlow.LOGIN);

        ResponseEntity<String> response = confirmError(LOGIN_CONFIRM_PATH + token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .contains(
                        "Account is locked or inactive. Please request account access restoration to continue.");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.INACTIVE);
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
    }

    private ResponseEntity<VerificationResponse> confirm(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>("", headers), VerificationResponse.class);
    }

    private ResponseEntity<String> confirmError(String path) {
        return restTemplate.exchange(path, HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> confirmationPage(String token) {
        return restTemplate.exchange(
                "/auth/verify/" + token, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
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
