package com.techindna.template.dto.auth;

import java.time.Instant;
import java.util.UUID;

public record VerificationResponse(String token, UserResponse user) {

    public record UserResponse(
            UUID id,
            String username,
            String firstName,
            String lastName,
            String email,
            String role,
            String userStatus,
            Instant createdAt,
            Instant updatedAt) {}
}
