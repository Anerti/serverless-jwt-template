package com.techindna.template.service.mail;

import com.techindna.template.entity.email.EmailDetails;
import com.techindna.template.entity.email.EmailTemplate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
            @Value("${spring.mail.username:}") String sender,
            SpringTemplateEngine templateEngine) {
        this.javaMailSender = javaMailSender;
        this.sender = sender;
        this.templateEngine = templateEngine;
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

            Context context = new Context(Locale.ENGLISH);
            context.setVariable("subject", details.getSubject());
            context.setVariable("body", details.getBody());
            if (details.getVariables() != null) {
                context.setVariables(details.getVariables());
            }
            String html = templateEngine.process(templateView(details.getTemplate()), context);
            helper.setText(html, true);

            javaMailSender.send(mimeMessage);
            log.info("Email sent to recipient with subject {}", details.getSubject());
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email with subject {}", details.getSubject(), e);
            throw new MailSendException("Failed to send email to recipient", e);
        }
    }

    private String templateView(EmailTemplate template) {
        return switch (template) {
            case REGISTRATION_VERIFICATION -> "mail/registration-verification";
            case LOGIN_VERIFICATION -> "mail/login-verification";
        };
    }
}