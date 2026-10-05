package com.techindna.template.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MailConfigurationTest {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private AsyncConfig asyncConfig;

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
    void mailExecutorBeanIsAvailable() {
        assertThat(asyncConfig.mailExecutor()).isNotNull();
    }
}