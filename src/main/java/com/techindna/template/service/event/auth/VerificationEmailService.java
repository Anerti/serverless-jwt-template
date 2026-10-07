package com.techindna.template.service.event.auth;

import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.mail.EmailService;
import com.techindna.template.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationEmailService {

    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendVerification(
            JUser user,
            HttpServletRequest request,
            EmailTemplate template,
            String subject,
            String body,
            String time,
            Map<String, Object> additionalVariables) {
        String token = verificationTokenService.createForUser(user.getId());
        String verificationLink =
                baseUrl.replaceAll("/+$", "") + "/auth/verify/" + token;

        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "Unknown";
        }

        Map<String, Object> variables =
                new HashMap<>(
                        Map.of(
                                "firstName", user.getFirstName(),
                                "verificationUrl", verificationLink,
                                "clientIp", request.getRemoteAddr(),
                                "userAgent", userAgent,
                                "time", time));
        variables.putAll(additionalVariables);

        try {
            emailService.sendMail(
                    new EmailDetails(template, user.getEmail(), subject, body, variables));
        } catch (MailException e) {
            verificationTokenService.delete(token);
            throw e;
        }
    }
}
