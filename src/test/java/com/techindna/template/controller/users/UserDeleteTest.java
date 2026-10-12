package com.techindna.template.controller.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mail.EmailService;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class UserDeleteTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String PASSWORD = "StrongPassword1!";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @MockitoBean private EmailService emailService;

    UserDeleteTest(
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
    void userCanDeleteOwnAccountAndReceivesNotification() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<Void> response = delete(user.getId().toString(), tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.findById(user.getId())).isEmpty();

        ArgumentCaptor<EmailDetails> emailCaptor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(emailCaptor.capture());
        EmailDetails email = emailCaptor.getValue();
        assertThat(email.getRecipient()).isEqualTo("jane@example.com");
        assertThat(email.getSubject()).isEqualTo("Your account was deleted");
        assertThat(email.getTemplate()).isEqualTo(EmailTemplate.ACCOUNT_DELETION_NOTIFICATION);
        assertThat(email.getVariables())
                .containsEntry("firstName", "Jane")
                .containsEntry("selfDeleted", true)
                .containsEntry("userAgent", "UserDeleteTest/1.0");
        assertThat((String) email.getVariables().get("clientIp")).isNotBlank();
        assertThat(Instant.parse((String) email.getVariables().get("time"))).isNotNull();
    }

    @Test
    void adminCanDeleteCustomerWithoutLeakingRequesterMetadata() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<Void> response = delete(customer.getId().toString(), tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(userRepository.findById(customer.getId())).isEmpty();
        assertThat(userRepository.findById(admin.getId())).isPresent();

        ArgumentCaptor<EmailDetails> emailCaptor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(emailCaptor.capture());
        EmailDetails email = emailCaptor.getValue();
        assertThat(email.getRecipient()).isEqualTo("jane@example.com");
        assertThat(email.getTemplate()).isEqualTo(EmailTemplate.ACCOUNT_DELETION_NOTIFICATION);
        assertThat(email.getVariables())
                .containsEntry("firstName", "Jane")
                .containsEntry("selfDeleted", false)
                .doesNotContainKeys("clientIp", "userAgent");
    }

    @Test
    void customerCannotDeleteAnotherUser() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        JUser other = saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                deleteRaw(other.getId().toString(), tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Cannot access this resource");
        assertThat(userRepository.findById(other.getId())).isPresent();
        verifyNoInteractions(emailService);
    }

    @Test
    void adminCannotDeleteAnotherAdmin() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        JUser otherAdmin = saveUser("root", "root@example.com", UserRole.ADMIN);

        ResponseEntity<String> response =
                deleteRaw(otherAdmin.getId().toString(), tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findById(otherAdmin.getId())).isPresent();
        verifyNoInteractions(emailService);
    }

    @Test
    void unknownUserReturnsNotFound() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                deleteRaw(UUID.randomUUID().toString(), tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("User not found");
        verifyNoInteractions(emailService);
    }

    @Test
    void missingTokenReturnsUnauthorized() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response = deleteRaw(customer.getId().toString(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.findById(customer.getId())).isPresent();
        verifyNoInteractions(emailService);
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                deleteRaw(customer.getId().toString(), "not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.findById(customer.getId())).isPresent();
        verifyNoInteractions(emailService);
    }

    @Test
    void malformedUserIdReturnsBadRequest() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response = deleteRaw("not-a-uuid", tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(emailService);
    }

    @Test
    void mailFailureReturnsServerErrorAndRollsBackDeletion() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(emailService)
                .sendMail(any(EmailDetails.class));

        ResponseEntity<String> response =
                deleteRaw(user.getId().toString(), tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .contains("Failed to send account-deletion notification email");
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    private JUser saveUser(String username, String email, UserRole role) {
        return userRepository.saveAndFlush(
                JUser.builder()
                        .username(username)
                        .password(passwordEncoder.encode(PASSWORD))
                        .firstName("Jane")
                        .lastName("Doe")
                        .email(email)
                        .verified(true)
                        .role(role)
                        .status(UserStatus.ACTIVE)
                        .build());
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(
                user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private ResponseEntity<Void> delete(String userId, String token) {
        return restTemplate.exchange(
                "/users/" + userId, HttpMethod.DELETE, request(token), Void.class);
    }

    private ResponseEntity<String> deleteRaw(String userId, String token) {
        return restTemplate.exchange(
                "/users/" + userId, HttpMethod.DELETE, request(token), String.class);
    }

    private HttpEntity<Void> request(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", CLIENT_IP);
        headers.set("User-Agent", "UserDeleteTest/1.0");
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }
}
