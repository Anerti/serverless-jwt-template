package com.techindna.template.service.mail;

import com.techindna.template.entity.email.EmailDetails;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class EmailSenderService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender javaMailSender;
    private final String sender;
    private final SpringTemplateEngine templateEngine;

    public EmailSenderService(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username:}") String sender) {
        this(javaMailSender, sender, null);
    }

    @Autowired
    public EmailSenderService(
            JavaMailSender javaMailSender,
            @Value("${spring.mail.username:}") String sender,
            SpringTemplateEngine templateEngine) {
        this.javaMailSender = javaMailSender;
        this.sender = sender;
        this.templateEngine = templateEngine;
    }

    @Async("mailExecutor")
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

            String content = templateEngine == null ? buildBody(details) : buildHtmlBody(details);
            helper.setText(content, templateEngine != null);

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

    private String buildHtmlBody(EmailDetails details) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("subject", details.getSubject());
        context.setVariable("body", details.getBody());

        Map<String, Object> variables = new LinkedHashMap<>();
        if (details.getVariables() != null) {
            details.getVariables().forEach((key, value) -> {
                if (value != null && !value.toString().isBlank()) {
                    variables.put(key, value);
                    context.setVariable(normalizeKey(key), value);
                }
            });
        }

        String verificationUrl = resolveVerificationUrl(details.getVariables());
        if (verificationUrl != null) {
            context.setVariable("verificationUrl", verificationUrl);
        }
        context.setVariable("variables", variables);

        return templateEngine.process("mail/verification", context);
    }

    private String resolveVerificationUrl(Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lowered = key.toLowerCase(Locale.ROOT);
            if (lowered.contains("verification") || lowered.contains("token") || lowered.contains("link")) {
                Object value = entry.getValue();
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "value";
        }
        String normalized = key.trim().replaceAll("[^A-Za-z0-9_]", "_");
        return normalized.isBlank() ? "value" : normalized;
    }
}