package com.techindna.template.service.event.auth;

import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.repository.model.JUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthVerificationEmailService {

    private final VerificationEmailService verificationEmailService;

    public void sendRegistrationVerification(JUser user, HttpServletRequest request) {
        verificationEmailService.sendVerification(
                user,
                request,
                EmailTemplate.REGISTRATION_VERIFICATION,
                "Verify your account",
                "Use the following link to verify your account:",
                DateTimeFormatter.ISO_INSTANT.format(user.getCreatedAt()),
                Map.of(
                        "lastName", user.getLastName(),
                        "username", user.getUsername(),
                        "email", user.getEmail()));
    }

    public void sendLoginVerification(JUser user, HttpServletRequest request) {
        verificationEmailService.sendVerification(
                user,
                request,
                EmailTemplate.LOGIN_VERIFICATION,
                "Verify your login",
                "Use the following link to verify your login:",
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                Map.of());
    }
}
