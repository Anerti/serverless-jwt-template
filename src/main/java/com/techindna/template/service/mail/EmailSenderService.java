package com.techindna.template.service.mail;

import com.techindna.template.entity.email.EmailDetails;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailSenderService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender javaMailSender;
    private final String sender;

    public EmailSenderService(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username:}") String sender) {
        this.javaMailSender = javaMailSender;
        this.sender = sender;
    }

    public void sendMail(EmailDetails details) {
        if (!StringUtils.hasText(sender)) {
            throw new MailSendException("Mail sender is not configured (spring.mail.username)");
        }
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(sender);
            helper.setTo(details.getRecipient());
            helper.setSubject(details.getSubject());
            helper.setText(buildBody(details));

            javaMailSender.send(mimeMessage);
            log.info("Email sent to recipient with subject {}", details.getSubject());
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email with subject {}", details.getSubject(), e);
            throw new MailSendException("Failed to send email to recipient", e);
        }
    }

    private String buildBody(EmailDetails details) {
        StringBuilder body = new StringBuilder();
        if (StringUtils.hasText(details.getBody())) {
            body.append(details.getBody().strip());
        }
        if (details.getVariables() != null) {
            details.getVariables().forEach((key, value) -> {
                if (value != null && !value.toString().isBlank()) {
                    if (!body.isEmpty()) {
                        body.append(System.lineSeparator());
                    }
                    body.append(key)
                            .append(": ")
                            .append(value);
                }
            });
        }
        if (body.isEmpty()) {
            body.append(details.getSubject());
        }
        return body.toString();
    }
}