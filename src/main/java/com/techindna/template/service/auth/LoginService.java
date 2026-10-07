package com.techindna.template.service.auth;

import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.LoginRequest;
import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.exception.http.ForbiddenException;
import com.techindna.template.exception.http.UnauthorizedException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.mail.EmailService;
import com.techindna.template.service.redis.LoginAttemptService;
import com.techindna.template.service.redis.VerificationTokenService;
import com.techindna.template.validator.AuthValidator;
import com.techindna.template.validator.DataValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;
    private final AuthValidator authValidator;
    private final DataValidator dataValidator;

    @Value("${app.base-url}")
    private String baseUrl;

    public MessageResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        authValidator.validateLogin(request);

        JUser user =
                (StringUtils.hasText(request.username())
                                ? userRepository.findByUsername(
                                        dataValidator.normalizeUsername(request.username()))
                                : userRepository.findByEmail(
                                        dataValidator.normalizeEmail(request.email())))
                        .orElseThrow(
                        () ->
                                new UnauthorizedException(
                                        String.format(
                                                "Invalid credentials. %d attempt(s) left", 4)));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new ForbiddenException(
                    "Your account is locked. Login is unavailable until the account is unlocked.");
        }

        if (passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.clear(user.getId());
            sendVerificationEmail(user, servletRequest);
            return new MessageResponse("A verification link has been sent to your email");
        }

        long attempts = loginAttemptService.recordFailure(user.getId());
        int remainingAttempts = (int) Math.max(0, MAX_ATTEMPTS - attempts);
        if (remainingAttempts == 0) {
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
            throw new ForbiddenException(
                    "Your account has been locked after too many unsuccessful login attempts.");
        }
        throw new UnauthorizedException(
                String.format(
                        "Invalid credentials. %d attempt(s) left", remainingAttempts));
    }

    private void sendVerificationEmail(JUser user, HttpServletRequest servletRequest) {
        String token = verificationTokenService.createForUser(user.getId());
        String verificationLink =
                baseUrl.replaceAll("/+$", "") + "/auth/verification/" + token;
        String userAgent = servletRequest.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "Unknown";
        }

        try {
            emailService.sendMail(
                    new EmailDetails(
                            EmailTemplate.LOGIN_VERIFICATION,
                            user.getEmail(),
                            "Verify your login",
                            "Use the following link to verify your login:",
                            Map.of(
                                    "firstName", user.getFirstName(),
                                    "verificationUrl", verificationLink,
                                    "clientIp", servletRequest.getRemoteAddr(),
                                    "userAgent", userAgent,
                                    "time",
                                    DateTimeFormatter.ISO_INSTANT.format(Instant.now()))));
        } catch (MailException e) {
            verificationTokenService.delete(token);
            throw e;
        }
    }
}
