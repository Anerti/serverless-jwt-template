package com.techindna.template.dto.auth;

public record ChangePasswordRequest(
        String oldPassword, String newPassword, String confirmNewPassword) {}
