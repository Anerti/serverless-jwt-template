package com.techindna.template.dto.auth;

import com.techindna.template.dto.UserResponse;

public record VerificationResponse(String token, UserResponse user) {}
