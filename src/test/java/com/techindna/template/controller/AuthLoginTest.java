package com.techindna.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.LoginRequest;
import com.techindna.template.dto.auth.VerificationResponse;
import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mail.EmailService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class AuthLoginTest extends TestcontainersConfig {

    private static final String USERNAME = "jane-doe";
    private static final String EMAIL = "jane.doe@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String VERIFICATION_KEY_PREFIX = "auth:verification:";
    private static final String LOGIN_ATTEMPT_KEY_PREFIX = "auth:login:attempts:";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final JwtTokenProvider jwtTokenProvider;

    @MockitoBean private EmailService emailService;

    AuthLoginTest(
            TestRestTemplate restTemplate,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis,
            JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        deleteRedisKeys(VERIFICATION_KEY_PREFIX + "*");
        deleteRedisKeys(LOGIN_ATTEMPT_KEY_PREFIX + "*");
    }

    @Test
    void validUsernameLoginSendsVerificationEmailAndCreatesToken() {
        JUser user = saveUser(true);
        String attemptsKey = LOGIN_ATTEMPT_KEY_PREFIX + user.getId();
        redis.opsForValue().set(attemptsKey, "3");

        ResponseEntity<MessageResponse> response =
                restTemplate.exchange(
                        "/auth/login",
                        HttpMethod.POST,
                        jsonRequest(new LoginRequest("  JANE-DOE ", null, PASSWORD)),
                        MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody())
                .isEqualTo(new MessageResponse("A verification link has been sent to your email"));
        assertThat(redis.opsForValue().get(attemptsKey)).isNull();

        Set<String> verificationKeys = redis.keys(VERIFICATION_KEY_PREFIX + "*");
        assertThat(verificationKeys).hasSize(1);
        String key = verificationKeys.iterator().next();
        String token = key.substring(VERIFICATION_KEY_PREFIX.length());
        assertThat(redis.opsForValue().get(key)).isEqualTo(user.getId().toString());

        ArgumentCaptor<EmailDetails> emailCaptor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(emailCaptor.capture());
        EmailDetails email = emailCaptor.getValue();
        assertThat(email.getRecipient()).isEqualTo(EMAIL);
        assertThat(email.getSubject()).isEqualTo("Verify your login");
        assertThat(email.getTemplate()).isEqualTo(EmailTemplate.LOGIN_VERIFICATION);
        assertThat(email.getVariables())
                .containsEntry("firstName", "Jane")
                .containsEntry(
                        "verificationUrl",
                        "http://localhost:8080/auth/verify/" + token)
                .containsEntry("userAgent", "AuthLoginTest/1.0");
    }

    @Test
    void validEmailLoginIsAccepted() {
        saveUser(true);

        ResponseEntity<MessageResponse> response =
                restTemplate.exchange(
                        "/auth/login",
                        HttpMethod.POST,
                        jsonRequest(new LoginRequest(null, "JANE.DOE@EXAMPLE.COM", PASSWORD)),
                        MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(emailService).sendMail(any(EmailDetails.class));
    }

    @Test
    void fullLoginProcessVerifiesEmailAndReturnsJwt() {
        JUser user = saveUser(true);

        ResponseEntity<MessageResponse> loginResponse =
                restTemplate.exchange(
                        "/auth/login",
                        HttpMethod.POST,
                        jsonRequest(new LoginRequest(USERNAME, null, PASSWORD)),
                        MessageResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Set<String> verificationKeys = redis.keys(VERIFICATION_KEY_PREFIX + "*");
        assertThat(verificationKeys).hasSize(1);
        String key = verificationKeys.iterator().next();
        String verificationToken = key.substring(VERIFICATION_KEY_PREFIX.length());
        assertThat(redis.opsForValue().get(key)).isEqualTo(user.getId().toString());

        ResponseEntity<String> confirmationPage =
                restTemplate.exchange(
                        "/auth/verify/" + verificationToken,
                        HttpMethod.GET,
                        HttpEntity.EMPTY,
                        String.class);

        assertThat(confirmationPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmationPage.getBody()).contains("method=\"post\"", "Confirm");
        assertThat(redis.opsForValue().get(key)).isEqualTo(user.getId().toString());

        ResponseEntity<VerificationResponse> verificationResponse =
                restTemplate.exchange(
                        "/auth/verify/" + verificationToken,
                        HttpMethod.POST,
                        new HttpEntity<>("", formRequestHeaders()),
                        VerificationResponse.class);

        assertThat(verificationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verificationResponse.getBody()).isNotNull();
        assertThat(verificationResponse.getBody().user().id()).isEqualTo(user.getId());
        assertThat(verificationResponse.getBody().user().username()).isEqualTo(USERNAME);
        assertThat(verificationResponse.getBody().user().email()).isEqualTo(EMAIL);
        assertThat(verificationResponse.getBody().user().role())
                .isEqualTo(UserRole.CUSTOMER.name().toLowerCase());
        assertThat(redis.opsForValue().get(key)).isNull();

        var claims = jwtTokenProvider.validateToken(verificationResponse.getBody().token());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(UserRole.CUSTOMER.name());
        assertThat(claims.get("ip_address", String.class)).isNotBlank();
    }

    @Test
    void usernameTakesPrecedenceWhenBothIdentifiersAreProvided() {
        JUser usernameUser = saveUser(true);
        userRepository.saveAndFlush(
                JUser.builder()
                        .username("another-user")
                        .password(passwordEncoder.encode(PASSWORD))
                        .firstName("John")
                        .lastName("Doe")
                        .email("john.doe@example.com")
                        .verified(true)
                        .role(UserRole.CUSTOMER)
                        .build());

        ResponseEntity<MessageResponse> response =
                restTemplate.exchange(
                        "/auth/login",
                        HttpMethod.POST,
                        jsonRequest(
                                new LoginRequest(USERNAME, "john.doe@example.com", PASSWORD)),
                        MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Set<String> verificationKeys = redis.keys(VERIFICATION_KEY_PREFIX + "*");
        assertThat(verificationKeys).hasSize(1);
        assertThat(redis.opsForValue().get(verificationKeys.iterator().next()))
                .isEqualTo(usernameUser.getId().toString());
        ArgumentCaptor<EmailDetails> emailCaptor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getRecipient()).isEqualTo(EMAIL);
    }

    @Test
    void invalidPasswordReturnsUnauthorizedAndTracksRemainingAttempts() {
        JUser user = saveUser(true);

        ResponseEntity<String> response =
                loginError(new LoginRequest(USERNAME, null, "WrongPassword1!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials. 4 attempt(s) left");
        assertThat(redis.opsForValue().get(LOGIN_ATTEMPT_KEY_PREFIX + user.getId())).isEqualTo("1");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(emailService);
    }

    @Test
    void fifthInvalidPasswordLocksAccountAndFurtherLoginIsForbidden() {
        JUser user = saveUser(true);

        for (int attempt = 1; attempt < 5; attempt++) {
            ResponseEntity<String> response =
                    loginError(new LoginRequest(USERNAME, null, "WrongPassword1!"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> lockResponse =
                loginError(new LoginRequest(USERNAME, null, "WrongPassword1!"));

        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(lockResponse.getBody()).contains("Account locked after too many");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.LOCKED);

        ResponseEntity<String> subsequentResponse =
                loginError(new LoginRequest(USERNAME, null, PASSWORD));
        assertThat(subsequentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subsequentResponse.getBody()).contains("This account is locked");
        verifyNoInteractions(emailService);
    }

    @Test
    void unknownUserReturnsUnauthorizedWithoutSendingEmail() {
        ResponseEntity<String> response =
                loginError(new LoginRequest("unknown-user", null, PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.count()).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void unknownUserAndIncorrectPasswordReturnDifferentAttemptCounts() {
        saveUser(true);

        ResponseEntity<String> unknownUser =
                loginError(new LoginRequest("unknown-user", null, PASSWORD));
        ResponseEntity<String> incorrectPassword =
                loginError(new LoginRequest(USERNAME, null, "WrongPassword1!"));

        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(incorrectPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownUser.getBody()).contains("5 attempt(s) left");
        assertThat(incorrectPassword.getBody()).contains("4 attempt(s) left");
        verifyNoInteractions(emailService);
    }

    @Test
    void unverifiedUserWithCorrectPasswordCannotStartLoginAndFailureCounterIsCleared() {
        JUser user = saveUser(false);
        String attemptsKey = LOGIN_ATTEMPT_KEY_PREFIX + user.getId();
        redis.opsForValue().set(attemptsKey, "2");

        ResponseEntity<String> response = loginError(new LoginRequest(USERNAME, null, PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Verify your email address");
        assertThat(redis.opsForValue().get(attemptsKey)).isNull();
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
        verifyNoInteractions(emailService);
    }

    @Test
    void unverifiedUserWithIncorrectPasswordGetsInvalidCredentialsResponse() {
        JUser user = saveUser(false);

        ResponseEntity<String> response =
                loginError(new LoginRequest(USERNAME, null, "WrongPassword1!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials. 4 attempt(s) left");
        assertThat(redis.opsForValue().get(LOGIN_ATTEMPT_KEY_PREFIX + user.getId()))
                .isEqualTo("1");
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
        verifyNoInteractions(emailService);
    }

    @Test
    void invalidCredentialsRequestIsRejectedBeforeLookup() {
        ResponseEntity<String> response =
                loginError(new LoginRequest(null, null, PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Username or email is required");
        assertThat(userRepository.count()).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void blankPasswordIsRejectedBeforeLookup() {
        ResponseEntity<String> response =
                loginError(new LoginRequest(USERNAME, null, "  "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("password is required and cannot be blank");
        assertThat(userRepository.count()).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void malformedAndOverlongIdentifiersAreRejectedBeforeLookup() {
        ResponseEntity<String> malformedEmail =
                loginError(new LoginRequest(null, "not-an-email", PASSWORD));
        ResponseEntity<String> overlongUsername =
                loginError(new LoginRequest("u".repeat(51), null, PASSWORD));

        assertThat(malformedEmail.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(malformedEmail.getBody()).contains("Email not-an-email is not valid");
        assertThat(overlongUsername.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(overlongUsername.getBody()).contains("username must not exceed 50 characters");
        assertThat(userRepository.count()).isZero();
        verifyNoInteractions(emailService);
    }

    @Test
    void inactiveVerifiedUserCanCurrentlyStartLogin() {
        JUser user = saveUser(true);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(user);

        ResponseEntity<MessageResponse> response =
                restTemplate.exchange(
                        "/auth/login",
                        HttpMethod.POST,
                        jsonRequest(new LoginRequest(USERNAME, null, PASSWORD)),
                        MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(emailService).sendMail(any(EmailDetails.class));
    }

    @Test
    void mailFailureReturnsServerErrorAndDeletesVerificationToken() {
        saveUser(true);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(emailService)
                .sendMail(any(EmailDetails.class));

        ResponseEntity<String> response =
                loginError(new LoginRequest(USERNAME, null, PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Something went wrong");
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
    }

    private JUser saveUser(boolean verified) {
        return userRepository.saveAndFlush(
                JUser.builder()
                        .username(USERNAME)
                        .password(passwordEncoder.encode(PASSWORD))
                        .firstName("Jane")
                        .lastName("Doe")
                        .email(EMAIL)
                        .verified(verified)
                        .role(UserRole.CUSTOMER)
                        .build());
    }

    private ResponseEntity<String> loginError(LoginRequest request) {
        return restTemplate.exchange(
                "/auth/login", HttpMethod.POST, jsonRequest(request), String.class);
    }

    private HttpEntity<LoginRequest> jsonRequest(LoginRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "AuthLoginTest/1.0");
        return new HttpEntity<>(request, headers);
    }

    private HttpHeaders formRequestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
