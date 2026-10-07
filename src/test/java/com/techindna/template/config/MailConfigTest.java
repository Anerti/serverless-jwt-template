package com.techindna.template.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import java.util.Properties;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@SpringBootTest
class MailConfigTest extends TestcontainersConfig {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void javaMailSenderTargetsConfiguredSmtpHost() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) javaMailSender;
        assertThat(sender.getHost()).isEqualTo("smtp.gmail.com");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getUsername()).isEqualTo("test@example.com");

        Properties props = sender.getJavaMailProperties();
        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");

        assertThat(Session.getInstance(props)).isNotNull();
    }

    @Test
    void registrationAndLoginUseDistinctEmailTemplates() {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(
                Map.of(
                        "firstName", "Jane",
                        "lastName", "Doe",
                        "username", "jane-doe",
                        "email", "jane@example.com",
                        "verificationUrl", "https://example.com/verify",
                        "clientIp", "127.0.0.1",
                        "userAgent", "JUnit",
                        "time", "2026-10-07T10:00:00Z"));

        String registration = templateEngine.process("mail/verification", context);
        String login = templateEngine.process("mail/login-verification", context);

        assertThat(registration)
                .contains("Thank you for registering", "Verify account")
                .doesNotContain("We received a login request");
        assertThat(login)
                .contains("A sign-in attempt was made", "Confirm sign-in", "Attempt at")
                .doesNotContain("Thank you for registering", "Username");
    }
}