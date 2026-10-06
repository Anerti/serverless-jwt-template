package com.techindna.template.dto.auth;

public record RegisterRequest(
        String username,
        String password,
        String confirmPassword,
        String firstName,
        String lastName,
        String email) {}
