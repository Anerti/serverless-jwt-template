package com.techindna.template.controller.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.template.config.TestcontainersConfig;
import com.techindna.template.dto.PageResponse;
import com.techindna.template.dto.UserResponse;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
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
class UserListTest extends TestcontainersConfig {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String PASSWORD = "StrongPassword1!";
    private static final ParameterizedTypeReference<PageResponse<UserResponse>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final TestRestTemplate restTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    UserListTest(
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
    void defaultsToCustomerRoleOnly() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> response = list("/users", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().total()).isEqualTo(2);
        assertThat(response.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("jane", "john");
    }

    @Test
    void filtersByAdminRole() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("root", "root@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> response = list("/users?role=admin", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().total()).isEqualTo(2);
        assertThat(response.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("admin", "root");
    }

    @Test
    void unknownRoleDefaultsToCustomer() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("root", "root@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> response = list("/users?role=superuser", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().meta().total()).isEqualTo(1);
        assertThat(response.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("jane");
    }

    @Test
    void combinesRoleFilterWithSearch() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        saveUser("janet-admin", "janet@example.com", UserRole.ADMIN);
        saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> response =
                list("/users?role=customer&search=jan", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("jane");
    }

    @Test
    void searchIsCaseInsensitive() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> response =
                list("/users?search=JANE", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("jane");
    }

    @Test
    void paginatesResults() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        saveUser("john", "john@example.com", UserRole.CUSTOMER);
        saveUser("alice", "alice@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> first =
                list("/users?page=1&size=1", admin);
        ResponseEntity<PageResponse<UserResponse>> second =
                list("/users?page=2&size=1", admin);

        assertThat(first.getBody()).isNotNull();
        assertThat(first.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("jane");
        assertThat(first.getBody().meta().page()).isEqualTo(1);
        assertThat(first.getBody().meta().size()).isEqualTo(1);
        assertThat(first.getBody().meta().total()).isEqualTo(3);

        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().data())
                .extracting(UserResponse::username)
                .containsExactly("john");
    }

    @Test
    void orderDescReversesCreationOrder() throws InterruptedException {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        saveUser("jane", "jane@example.com", UserRole.CUSTOMER);
        Thread.sleep(5);
        saveUser("john", "john@example.com", UserRole.CUSTOMER);

        ResponseEntity<PageResponse<UserResponse>> asc = list("/users?order=asc", admin);
        ResponseEntity<PageResponse<UserResponse>> desc = list("/users?order=desc", admin);

        List<String> usersAsc = asc.getBody().data().stream()
                .map(UserResponse::username)
                .toList();
        List<String> usersDesc = desc.getBody().data().stream()
                .map(UserResponse::username)
                .toList();

        assertThat(usersAsc).containsExactly("jane", "john");
        assertThat(usersDesc).containsExactly("john", "jane");
    }

    @Test
    void invalidSearchCharactersReturnsUnprocessable() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users?search=abc$", HttpMethod.GET, request(admin), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("Search may only contain letters, digits, dot, @, underscore, hyphen, space, and éèê");
    }

    @Test
    void searchTooLongReturnsUnprocessable() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);
        String longSearch = "a".repeat(101);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users?search=" + longSearch, HttpMethod.GET, request(admin), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("search must not exceed 100 characters");
    }

    @Test
    void invalidPaginationReturnsBadRequest() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users?page=0", HttpMethod.GET, request(admin), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid pagination parameters");
    }

    @Test
    void missingTokenReturnsUnauthorized() {
        JUser admin = saveUser("admin", "admin@example.com", UserRole.ADMIN);

        ResponseEntity<String> response =
                restTemplate.exchange("/users", HttpMethod.GET, request(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonAdminReturnsForbidden() {
        JUser customer = saveUser("jane", "jane@example.com", UserRole.CUSTOMER);

        ResponseEntity<String> response =
                restTemplate.exchange("/users", HttpMethod.GET, request(customer), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<PageResponse<UserResponse>> list(String path, JUser caller) {
        return restTemplate.exchange(path, HttpMethod.GET, request(caller), PAGE_TYPE);
    }

    private JUser saveUser(String username, String email, UserRole role) {
        return userRepository.saveAndFlush(
                JUser.builder()
                        .username(username)
                        .password(passwordEncoder.encode(PASSWORD))
                        .firstName(
                                Character.toUpperCase(username.charAt(0)) + username.substring(1))
                        .lastName("Doe")
                        .email(email)
                        .verified(true)
                        .role(role)
                        .status(UserStatus.ACTIVE)
                        .build());
    }

    private HttpEntity<Void> request(JUser caller) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", CLIENT_IP);
        if (caller != null) {
            headers.setBearerAuth(
                    jwtTokenProvider.generateToken(
                            caller.getId().toString(), caller.getRole().name(), CLIENT_IP));
        }
        return new HttpEntity<>(headers);
    }
}