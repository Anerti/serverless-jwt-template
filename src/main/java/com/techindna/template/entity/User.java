package com.techindna.template.entity;

import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String email,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
