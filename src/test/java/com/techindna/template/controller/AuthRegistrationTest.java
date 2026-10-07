package com.techindna.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.mail.EmailService;
import java.time.Instant;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class AuthRegistrationTest extends TestcontainersConfig {

    private static final String USERNAME = "jane-doe";
    private static final String EMAIL = "jane.doe@example.com";
    private static final String VERIFICATION_KEY_PREFIX = "auth:verification:";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    @MockitoBean private EmailService emailService;

    AuthRegistrationTest(
            TestRestTemplate restTemplate,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        Set<String> verificationKeys = redis.keys(VERIFICATION_KEY_PREFIX + "*");
        if (verificationKeys != null && !verificationKeys.isEmpty()) {
            redis.delete(verificationKeys);
        }
    }

    @Test
    void validRegistrationCreatesUnverifiedUserAndSendsVerificationEmail() {
        ResponseEntity<MessageResponse> response = register(validRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("An email has been sent to verify your account");

        JUser user = userRepository.findAll().getFirst();
        assertThat(user.getUsername()).isEqualTo(USERNAME);
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getVerified()).isFalse();
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getPassword()).isNotEqualTo("StrongPassword1!");
        assertThat(passwordEncoder.matches("StrongPassword1!", user.getPassword())).isTrue();

        Set<String> keys = redis.keys(VERIFICATION_KEY_PREFIX + "*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        String token = key.substring(VERIFICATION_KEY_PREFIX.length());
        assertThat(redis.opsForValue().get(key)).isEqualTo(user.getId().toString());

        ArgumentCaptor<EmailDetails> emailCaptor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(emailCaptor.capture());
        EmailDetails email = emailCaptor.getValue();
        assertThat(email.getRecipient()).isEqualTo(EMAIL);
        assertThat(email.getSubject()).isEqualTo("Verify your account");
        assertThat(email.getVariables())
                .containsEntry("firstName", "Jane")
                .containsEntry("lastName", "Doe")
                .containsEntry("username", USERNAME)
                .containsEntry("email", EMAIL)
                .containsEntry(
                        "verificationUrl",
                        "http://localhost:8080/auth/verification/" + token)
                .containsEntry("userAgent", "AuthRegistrationControllerTest/1.0");
        assertThat((String) email.getVariables().get("clientIp")).isNotBlank();
        assertThat(Instant.parse((String) email.getVariables().get("time"))).isNotNull();
    }

    @Test
    void shortPasswordReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, "weak", "weak", "Jane", "Doe", EMAIL),
                "Password must be at least 12 characters");
    }

    @Test
    void blankUsernameReturnsUnprocessableContent() {
        assertUnprocessable(request("", validPassword(), validPassword(), "Jane", "Doe", EMAIL),
                "username is required and cannot be blank");
    }

    @Test
    void invalidUsernameReturnsUnprocessableContent() {
        assertUnprocessable(request("j", validPassword(), validPassword(), "Jane", "Doe", EMAIL),
                "Username j is invalid");
    }

    @Test
    void overlongUsernameReturnsUnprocessableContent() {
        assertUnprocessable(
                request("j".repeat(51), validPassword(), validPassword(), "Jane", "Doe", EMAIL),
                "username must not exceed 50 characters");
    }

    @Test
    void blankPasswordReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, "", "", "Jane", "Doe", EMAIL),
                "password is required and cannot be blank");
    }

    @Test
    void passwordWithoutUppercaseReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, "strongpassword1!", "strongpassword1!",
                        "Jane", "Doe", EMAIL),
                "Password must contain at least one uppercase character");
    }

    @Test
    void passwordWithoutLowercaseReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, "STRONGPASSWORD1!", "STRONGPASSWORD1!",
                        "Jane", "Doe", EMAIL),
                "Password must contain at least one lowercase character");
    }

    @Test
    void passwordWithoutDigitReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, "StrongPassword!!", "StrongPassword!!",
                        "Jane", "Doe", EMAIL),
                "Password must contain at least one digit");
    }

    @Test
    void passwordWithoutSpecialCharacterReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, "StrongPassword12", "StrongPassword12",
                        "Jane", "Doe", EMAIL),
                "Password must contain at least one special character");
    }

    @Test
    void blankConfirmationPasswordReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, validPassword(), "", "Jane", "Doe", EMAIL),
                "confirmPassword is required and cannot be blank");
    }

    @Test
    void mismatchedPasswordsReturnUnprocessableContent() {
        assertUnprocessable(request(USERNAME, validPassword(), "DifferentPassword1!",
                        "Jane", "Doe", EMAIL),
                "Password and confirmPassword must match");
    }

    @Test
    void invalidFirstNameReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, validPassword(), validPassword(), "jane", "Doe", EMAIL),
                "firstName must start with a capital letter");
    }

    @Test
    void overlongNameReturnsUnprocessableContent() {
        assertUnprocessable(
                request(USERNAME, validPassword(), validPassword(), "J" + "a".repeat(100), "Doe",
                        EMAIL),
                "firstName must not exceed 100 characters");
    }

    @Test
    void invalidLastNameReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, validPassword(), validPassword(), "Jane", "D0e", EMAIL),
                "lastName must start with a capital letter");
    }

    @Test
    void blankEmailReturnsUnprocessableContent() {
        assertUnprocessable(request(USERNAME, validPassword(), validPassword(), "Jane", "Doe", ""),
                "email is required and cannot be blank");
    }

    @Test
    void invalidEmailReturnsUnprocessableContent() {
        assertUnprocessable(
                request(USERNAME, validPassword(), validPassword(), "Jane", "Doe", "not-an-email"),
                "Email not-an-email is not valid");
    }

    @Test
    void emailWithMultiLabelDomainIsAccepted() {
        ResponseEntity<MessageResponse> response = register(
                request("multi-label-domain", validPassword(), validPassword(), "Jane", "Doe",
                        "name@sub.example.co.uk"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(userRepository.findAll())
                .singleElement()
                .extracting(JUser::getEmail)
                .isEqualTo("name@sub.example.co.uk");
    }

    @Test
    void emailWithPlusAddressingIsRejected() {
        assertUnprocessable(
                request(USERNAME, validPassword(), validPassword(), "Jane", "Doe",
                        "name+tag@example.com"),
                "Email name+tag@example.com is not valid");
    }

    @Test
    void overlongEmailReturnsUnprocessableContent() {
        String longEmail = "a".repeat(89) + "@example.com";
        assertUnprocessable(request(
                        USERNAME, validPassword(), validPassword(), "Jane", "Doe", longEmail),
                "email must not exceed 100 characters");
    }

    @Test
    void duplicateEmailReturnsConflict() {
        saveExistingUser("existing-user", EMAIL);

        ResponseEntity<String> response = registerError(
                request("new-user", validPassword(), validPassword(), "Jane", "Doe", EMAIL));

        assertConflict(response, "You cannot use this email address");
    }

    @Test
    void duplicateUsernameReturnsConflict() {
        saveExistingUser(USERNAME, "existing@example.com");

        ResponseEntity<String> response = registerError(
                request(USERNAME, validPassword(), validPassword(), "Jane", "Doe", EMAIL));

        assertConflict(response, "You cannot use this username");
    }

    @Test
    void mixedCaseUsernameAndEmailAreNormalizedBeforePersistence() {
        saveExistingUser("jane-doe", "jane.doe@example.com");

        ResponseEntity<String> response = registerError(
                request("Jane-Doe", validPassword(), validPassword(), "Jane", "Doe",
                        "Jane.Doe@Example.com"));

        assertConflict(response, "You cannot use this username");
    }

    @Test
    void failedVerificationEmailReturnsServerErrorAndRollsBackRegistration() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(emailService)
                .sendMail(any(EmailDetails.class));

        ResponseEntity<String> response = registerError(validRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Something went wrong");
        assertThat(userRepository.count()).isZero();
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void missingRequestBodyReturnsBadRequest() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/register",
                HttpMethod.POST,
                jsonRequest(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertNoRegistrationSideEffects();
    }

    private RegisterRequest validRequest() {
        return request(USERNAME, validPassword(), validPassword(), "Jane", "Doe", EMAIL);
    }

    private ResponseEntity<MessageResponse> register(RegisterRequest request) {
        return restTemplate.exchange(
                "/auth/register",
                HttpMethod.POST,
                jsonRequest(request),
                MessageResponse.class);
    }

    private ResponseEntity<String> registerError(RegisterRequest request) {
        return restTemplate.exchange(
                "/auth/register",
                HttpMethod.POST,
                jsonRequest(request),
                String.class);
    }

    private HttpEntity<RegisterRequest> jsonRequest(RegisterRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "AuthRegistrationControllerTest/1.0");
        return new HttpEntity<>(request, headers);
    }

    private HttpEntity<String> jsonRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>("", headers);
    }

    private RegisterRequest request(
            String username,
            String password,
            String confirmPassword,
            String firstName,
            String lastName,
            String email) {
        return new RegisterRequest(username, password, confirmPassword, firstName, lastName, email);
    }

    private String validPassword() {
        return "StrongPassword1!";
    }

    private void assertUnprocessable(RegisterRequest request, String expectedMessage) {
        ResponseEntity<String> response = registerError(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains(expectedMessage);
        assertNoRegistrationSideEffects();
    }

    private void assertConflict(ResponseEntity<String> response, String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains(expectedMessage);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
        verifyNoInteractions(emailService);
    }

    private void assertNoRegistrationSideEffects() {
        assertThat(userRepository.count()).isZero();
        assertThat(redis.keys(VERIFICATION_KEY_PREFIX + "*")).isEmpty();
        verifyNoInteractions(emailService);
    }

    private void saveExistingUser(String username, String email) {
        userRepository.saveAndFlush(
                JUser.builder()
                        .username(username)
                        .password(passwordEncoder.encode(validPassword()))
                        .firstName("John")
                        .lastName("Doe")
                        .email(email)
                        .verified(true)
                        .role(UserRole.CUSTOMER)
                        .build());
    }
}
