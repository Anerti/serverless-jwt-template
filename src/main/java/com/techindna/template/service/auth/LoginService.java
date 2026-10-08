package com.techindna.template.service.auth;

import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.LoginRequest;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.exception.http.ForbiddenException;
import com.techindna.template.exception.http.UnauthorizedException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.event.auth.AuthVerificationEmailService;
import com.techindna.template.service.redis.LoginAttemptService;
import com.techindna.template.validator.AuthValidator;
import com.techindna.template.validator.DataValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AuthVerificationEmailService verificationEmailService;
    private final AuthValidator authValidator;
    private final DataValidator dataValidator;

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
                                        new UnauthorizedException(String.format(
                                                "Invalid credentials. %d attempt(s) left", 5)));

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new ForbiddenException(
                    "Account locked or inactive. Please request account access restoration to continue.");
        }

        if (passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.clear(user.getId());
            if (!Boolean.TRUE.equals(user.getVerified())) {
                throw new ForbiddenException(
                        "Verify your email address before signing in.");
            }
            verificationEmailService.sendLoginVerification(user, servletRequest);
            return new MessageResponse("A verification link has been sent to your email");
        }

        long attempts = loginAttemptService.recordFailure(user.getId());
        int remainingAttempts = (int) Math.max(0, MAX_ATTEMPTS - attempts);
        if (remainingAttempts == 0) {
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
            throw new ForbiddenException(
                    "Account locked after too many unsuccessful sign-in attempts.");
        }
        throw new UnauthorizedException(String.format("Invalid credentials. %d attempt(s) left", remainingAttempts));
    }

}
