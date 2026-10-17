package com.techindna.template.controller.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.UpdateUserRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestConstructor(autowireMode = AutowireMode.ALL)
class UserUpdateTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String PASSWORD = "StrongPassword1!";

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    UserUpdateTest(
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
    void userCanPartiallyUpdateOwnProfile() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response =
                patch(
                        user.getId().toString(),
                        new UpdateUserRequest(null, "Janet", null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(user.getId());
        assertThat(response.getBody().firstName()).isEqualTo("Janet");
        assertThat(response.getBody().lastName()).isEqualTo("Doe");
        assertThat(response.getBody().username()).isEqualTo("jane");
        assertThat(response.getBody().email()).isEqualTo("jane@example.com");
        assertThat(response.getBody().role()).isEqualTo("customer");
        assertThat(response.getBody().userStatus()).isEqualTo("active");
        assertThat(response.getBody().updatedAt()).isNotNull();

        JUser reloaded = reload(user);
        assertThat(reloaded.getFirstName()).isEqualTo("Janet");
        assertThat(reloaded.getLastName()).isEqualTo("Doe");
        assertThat(reloaded.getUsername()).isEqualTo("jane");
        assertThat(reloaded.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void userCanUpdateAllProfileFields() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response =
                patch(
                        user.getId().toString(),
                        new UpdateUserRequest("jane-new", "Janet", "Smith"),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("jane-new");
        assertThat(response.getBody().firstName()).isEqualTo("Janet");
        assertThat(response.getBody().lastName()).isEqualTo("Smith");

        JUser reloaded = reload(user);
        assertThat(reloaded.getUsername()).isEqualTo("jane-new");
        assertThat(reloaded.getFirstName()).isEqualTo("Janet");
        assertThat(reloaded.getLastName()).isEqualTo("Smith");
    }

    @Test
    void emptyBodyLeavesUserUnchanged() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response =
                patch(
                        user.getId().toString(),
                        new UpdateUserRequest(null, null, null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("jane");
        assertThat(response.getBody().firstName()).isEqualTo("Jane");
        assertThat(response.getBody().lastName()).isEqualTo("Doe");
    }

    @Test
    void usernameIsNormalizedAndTrimmedOnUpdate() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response =
                patch(
                        user.getId().toString(),
                        new UpdateUserRequest("  JANE_2  ", null, null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("jane_2");
        assertThat(reload(user).getUsername()).isEqualTo("jane_2");
    }

    @Test
    void caseOnlyUsernameChangeIsAllowed() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<UserResponse> response =
                patch(
                        user.getId().toString(),
                        new UpdateUserRequest("JANE", null, null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("jane");
    }

    @Test
    void duplicateUsernameReturnsConflictAndKeepsCurrentUsername() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest("john", null, null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Cannot use this username");
        assertThat(reload(user).getUsername()).isEqualTo("jane");
    }

    @Test
    void customerCannotUpdateAnotherCustomer() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        JUser other = saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        other.getId().toString(),
                        new UpdateUserRequest(null, "Hacked", null),
                        tokenFor(customer));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Cannot access this resource");
        assertThat(reload(other).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void adminCannotUpdateCustomerProfile() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        customer.getId().toString(),
                        new UpdateUserRequest(null, "Hacked", null),
                        tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Cannot access this resource");
        assertThat(reload(customer).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void adminCannotUpdateAnotherAdminProfile() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        JUser otherAdmin = saveUser("root", "root@example.com", UserRole.ADMIN);

        ResponseEntity<String> response =
                patchRaw(
                        otherAdmin.getId().toString(),
                        new UpdateUserRequest(null, "Hacked", null),
                        tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Cannot access this resource");
        assertThat(reload(otherAdmin).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void adminCanUpdateOwnProfile() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);

        ResponseEntity<UserResponse> response =
                patch(
                        admin.getId().toString(),
                        new UpdateUserRequest(null, "Adam", null),
                        tokenFor(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().firstName()).isEqualTo("Adam");
        assertThat(response.getBody().role()).isEqualTo("admin");
    }

    @Test
    void unknownUserReturnsForbidden() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        UUID.randomUUID().toString(),
                        new UpdateUserRequest(null, "Janet", null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Cannot access this resource");
    }

    @Test
    void missingTokenReturnsUnauthorized() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest(null, "Janet", null),
                        null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reload(user).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest(null, "Janet", null),
                        "not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reload(user).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void malformedUserIdReturnsBadRequest() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw("not-a-uuid", new UpdateUserRequest(null, "Janet", null), tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidUsernameReturnsUnprocessableContent() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest("a", null, null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Username a is invalid");
        assertThat(reload(user).getUsername()).isEqualTo("jane");
    }

    @Test
    void tooLongUsernameReturnsUnprocessableContent() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest("a".repeat(51), null, null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("username must not exceed 50 characters");
    }

    @Test
    void invalidFirstNameReturnsUnprocessableContent() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest(null, "john", null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("firstName must start with a capital letter");
        assertThat(reload(user).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void tooLongFirstNameReturnsUnprocessableContent() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest(null, "J" + "a".repeat(100), null),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("firstName must not exceed 100 characters");
    }

    @Test
    void invalidLastNameReturnsUnprocessableContent() {
        JUser user = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                patchRaw(
                        user.getId().toString(),
                        new UpdateUserRequest(null, null, "doe"),
                        tokenFor(user));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("lastName must start with a capital letter");
        assertThat(reload(user).getLastName()).isEqualTo("Doe");
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

    private JUser reload(JUser user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private String tokenFor(JUser user) {
        return jwtTokenProvider.generateToken(
                user.getId().toString(), user.getRole().name(), CLIENT_IP);
    }

    private ResponseEntity<UserResponse> patch(
            String userId, UpdateUserRequest body, String token) {
        return restTemplate.exchange(
                "/users/" + userId, HttpMethod.PATCH, request(body, token), UserResponse.class);
    }

    private ResponseEntity<String> patchRaw(
            String userId, UpdateUserRequest body, String token) {
        return restTemplate.exchange(
                "/users/" + userId, HttpMethod.PATCH, request(body, token), String.class);
    }

    private HttpEntity<UpdateUserRequest> request(UpdateUserRequest body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", CLIENT_IP);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }
}
