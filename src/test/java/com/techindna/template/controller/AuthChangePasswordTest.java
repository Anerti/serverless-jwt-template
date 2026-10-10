package com.techindna.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.ChangePasswordRequest;
import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mail.EmailService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
class AuthChangePasswordTest extends TestcontainersConfig {

    private static final String USERNAME = "jane-doe";
    private static final String EMAIL = "jane.doe@example.com";
    private static final String OLD_PASSWORD = "StrongPassword1!";
    private static final String NEW_PASSWORD = "EvenStronger2@";
    private static final String CLIENT_IP = "203.0.113.5";
    private static final String ENDPOINT = "/auth/change-password";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @MockitoBean private EmailService emailService;

    AuthChangePasswordTest(
            TestRestTemplate restTemplate,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    void changesPasswordAndSendsNotificationEmail() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<MessageResponse> response =
                change(validRequest(), tokenFor(user), MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(new MessageResponse("Password changed successfully"));

        JUser updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, updated.getPassword())).isFalse();

        ArgumentCaptor<EmailDetails> emailCaptor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(emailCaptor.capture());
        EmailDetails email = emailCaptor.getValue();
        assertThat(email.getRecipient()).isEqualTo(EMAIL);
        assertThat(email.getSubject()).isEqualTo("Your password was changed");
        assertThat(email.getTemplate()).isEqualTo(EmailTemplate.PASSWORD_CHANGE_NOTIFICATION);
        assertThat(email.getVariables())
                .containsEntry("firstName", "Jane")
                .containsEntry("userAgent", "AuthChangePasswordTest/1.0");
        assertThat((String) email.getVariables().get("clientIp")).isNotBlank();
        assertThat(Instant.parse((String) email.getVariables().get("time"))).isNotNull();
    }

    @Test
    void wrongOldPasswordReturnsUnauthorizedAndKeepsPassword() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest("WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reload(user).getPassword())).isTrue();
        verifyNoInteractions(emailService);
    }

    @Test
    void missingTokenReturnsUnauthorized() {
        ResponseEntity<String> response = change(validRequest(), null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(emailService);
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        ResponseEntity<String> response = change(validRequest(), "not-a-jwt", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(emailService);
    }

    @Test
    void unverifiedUserIsForbidden() {
        JUser user = saveUser(false, UserStatus.ACTIVE);

        ResponseEntity<String> response = change(validRequest(), tokenFor(user), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Account has not been verified");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reload(user).getPassword())).isTrue();
        verifyNoInteractions(emailService);
    }

    @Test
    void lockedUserIsForbidden() {
        JUser user = saveUser(true, UserStatus.LOCKED);

        ResponseEntity<String> response = change(validRequest(), tokenFor(user), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Account locked or inactive");
        verifyNoInteractions(emailService);
    }

    @Test
    void shortNewPasswordReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(OLD_PASSWORD, "Short1!", "Short1!"),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("New password must be at least 12 characters");
        verifyNoInteractions(emailService);
    }

    @Test
    void mismatchedConfirmationReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(OLD_PASSWORD, NEW_PASSWORD, "DifferentPassword1!"),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Passwords do not match");
        verifyNoInteractions(emailService);
    }

    @Test
    void blankOldPasswordReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest("  ", NEW_PASSWORD, NEW_PASSWORD),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("oldPassword is required and cannot be blank");
        verifyNoInteractions(emailService);
    }

    @Test
    void mailFailureReturnsServerErrorAndRollsBackPassword() {
        JUser user = saveUser(true, UserStatus.ACTIVE);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(emailService)
                .sendMail(any(EmailDetails.class));

        ResponseEntity<String> response = change(validRequest(), tokenFor(user), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .contains("Failed to send password-change notification email");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reload(user).getPassword())).isTrue();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reload(user).getPassword())).isFalse();
    }

    @Test
    void reusingCurrentPasswordReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("New password must differ from the current password");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reload(user).getPassword())).isTrue();
        verifyNoInteractions(emailService);
    }

    @Test
    void newPasswordWithoutUppercaseReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(
                                OLD_PASSWORD, "lowercase1234!", "lowercase1234!"),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("New password must contain at least one uppercase character");
        verifyNoInteractions(emailService);
    }

    @Test
    void newPasswordWithoutLowercaseReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(
                                OLD_PASSWORD, "UPPERCASE1234!", "UPPERCASE1234!"),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("New password must contain at least one lowercase character");
        verifyNoInteractions(emailService);
    }

    @Test
    void newPasswordWithoutDigitReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(OLD_PASSWORD, "UppercasePass!", "UppercasePass!"),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("New password must contain at least one digit");
        verifyNoInteractions(emailService);
    }

    @Test
    void newPasswordWithoutSpecialCharacterReturnsUnprocessableContent() {
        JUser user = saveUser(true, UserStatus.ACTIVE);

        ResponseEntity<String> response =
                change(
                        new ChangePasswordRequest(OLD_PASSWORD, "Uppercasepass12", "Uppercasepass12"),
                        tokenFor(user),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("New password must contain at least one special character");
        verifyNoInteractions(emailService);
    }

    private ChangePasswordRequest validRequest() {
        return new ChangePasswordRequest(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
    }

    private JUser saveUser(boolean verified, UserStatus status) {
        return userRepository.saveAndFlush(
                JUser.builder()
                        .username(USERNAME)
                        .password(passwordEncoder.encode(OLD_PASSWORD))
                        .firstName("Jane")
                        .lastName("Doe")
                        .email(EMAIL)
                        .verified(verified)
                        .role(UserRole.CUSTOMER)
                        .status(status)
                        .build());
    }

    private JUser reload(JUser user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(
                user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private <T> ResponseEntity<T> change(
            ChangePasswordRequest request, String token, Class<T> responseType) {
        return restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, jsonRequest(request, token), responseType);
    }

    private HttpEntity<ChangePasswordRequest> jsonRequest(
            ChangePasswordRequest request, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", CLIENT_IP);
        headers.set("User-Agent", "AuthChangePasswordTest/1.0");
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(request, headers);
    }
}
