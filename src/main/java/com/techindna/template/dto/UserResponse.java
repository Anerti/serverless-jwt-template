package com.techindna.template.dto;

import java.time.Instant;
import java.util.UUID;

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
