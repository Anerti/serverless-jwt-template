package com.techindna.template.service.auth;

import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.exception.http.ConflictException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.mail.EmailService;
import com.techindna.template.service.mapper.UserMapper;
import com.techindna.template.service.redis.VerificationTokenService;
import com.techindna.template.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final AuthValidator authValidator;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public MessageResponse register(RegisterRequest request, HttpServletRequest servletRequest) {
        authValidator.validateRegistration(request);

        JUser user = userMapper.toPersistenceModel(request);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            String constraint = e.getMostSpecificCause().getMessage();
            if (constraint != null && constraint.contains("email")) {
                throw new ConflictException("You cannot use this email address");
            }
            if (constraint != null && constraint.contains("username")) {
                throw new ConflictException("You cannot use this username");
            }
            throw e;
        }

        sendVerificationEmail(user, servletRequest);

        return new MessageResponse("An email has been sent to verify your account");
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
                            user.getEmail(),
                            "Verify your account",
                            "Use the following link to verify your account:",
                            Map.of(
                                    "firstName", user.getFirstName(),
                                    "lastName", user.getLastName(),
                                    "username", user.getUsername(),
                                    "email", user.getEmail(),
                                    "verificationUrl", verificationLink,
                                    "clientIp", servletRequest.getRemoteAddr(),
                                    "userAgent", userAgent,
                                    "time",
                                    DateTimeFormatter.ISO_INSTANT.format(user.getCreatedAt()))));
        } catch (MailException e) {
            verificationTokenService.delete(token);
            throw e;
        }
    }
}
