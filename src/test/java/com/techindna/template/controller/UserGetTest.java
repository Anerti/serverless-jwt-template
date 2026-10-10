package com.techindna.template.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.UserResponse;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class UserGetTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String PASSWORD = "StrongPassword1!";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    UserGetTest(
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
    void userCanGetOwnProfile() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response = get(user.getId().toString(), tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(user.getId());
        assertThat(response.getBody().username()).isEqualTo("jane");
        assertThat(response.getBody().firstName()).isEqualTo("Jane");
        assertThat(response.getBody().lastName()).isEqualTo("Doe");
        assertThat(response.getBody().email()).isEqualTo("jane@example.com");
        assertThat(response.getBody().role()).isEqualTo("customer");
        assertThat(response.getBody().userStatus()).isEqualTo("active");
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void adminCanGetCustomerProfile() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response =
                get(customer.getId().toString(), tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(customer.getId());
        assertThat(response.getBody().role()).isEqualTo("customer");
    }

    @Test
    void customerCannotGetAnotherUser() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        JUser other = saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                getRaw(other.getId().toString(), tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Cannot access this resource");
    }

    @Test
    void adminCannotGetAnotherAdminProfile() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        JUser otherAdmin = saveUser("root", "root@example.com", UserRole.ADMIN);

        ResponseEntity<String> response =
                getRaw(otherAdmin.getId().toString(), tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownUserReturnsNotFound() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                getRaw(UUID.randomUUID().toString(), tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("User not found");
    }

    @Test
    void missingTokenReturnsUnauthorized() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response = getRaw(customer.getId().toString(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                getRaw(customer.getId().toString(), "not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedUserIdReturnsBadRequest() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response = getRaw("not-a-uuid", tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

    private ResponseEntity<UserResponse> get(String userId, String token) {
        return restTemplate.exchange(
                "/users/" + userId, HttpMethod.GET, request(token), UserResponse.class);
    }

    private ResponseEntity<String> getRaw(String userId, String token) {
        return restTemplate.exchange(
                "/users/" + userId, HttpMethod.GET, request(token), String.class);
    }

    private HttpEntity<Void> request(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", CLIENT_IP);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(headers);
    }
}
