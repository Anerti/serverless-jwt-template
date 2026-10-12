package com.techindna.template.service.event;

import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import com.techindna.template.exception.http.InternalServerErrorException;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.mail.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountDeletionNotificationService {

    private static final String SUBJECT = "Your account was deleted";
    private static final String FAILURE_MESSAGE = "Failed to send account-deletion notification email";

    private final EmailService emailService;

    public void sendAccountDeletionNotification(JUser user, HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "Unknown";
        }

        Map<String, Object> variables =
                Map.of(
                        "firstName", user.getFirstName(),
                        "clientIp", request.getRemoteAddr(),
                        "userAgent", userAgent,
                        "time", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        try {
            emailService.sendMail(
                    new EmailDetails(
                            EmailTemplate.ACCOUNT_DELETION_NOTIFICATION,
                            user.getEmail(),
                            SUBJECT,
                            "Your account has been deleted.",
                            variables));
        } catch (MailException e) {
            throw new InternalServerErrorException(FAILURE_MESSAGE, e);
        }
    }
}
