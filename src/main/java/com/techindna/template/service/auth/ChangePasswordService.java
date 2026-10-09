package com.techindna.template.service.auth;

import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.ChangePasswordRequest;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.exception.http.ForbiddenException;
import com.techindna.template.exception.http.UnauthorizedException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.event.auth.PasswordChangeNotificationService;
import com.techindna.template.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthValidator authValidator;
    private final PasswordChangeNotificationService notificationService;

    @Transactional
    public MessageResponse changePassword(
            String userId, ChangePasswordRequest request, HttpServletRequest servletRequest) {
        authValidator.validateChangePassword(request);

        JUser user = resolveUser(userId);

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new ForbiddenException(
                    "Account locked or inactive. Please request account access restoration to continue.");
        }

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (Boolean.FALSE.equals(user.getVerified())) {
            throw new ForbiddenException("Account has not been verified");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        notificationService.sendPasswordChangeNotification(user, servletRequest);

        return new MessageResponse("Password changed successfully");
    }

    private JUser resolveUser(String userId) {
        try {
            return userRepository
                    .findById(UUID.fromString(userId))
                    .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
    }
}
