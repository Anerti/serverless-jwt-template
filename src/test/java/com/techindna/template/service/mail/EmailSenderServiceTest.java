package com.techindna.template.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techindna.template.entity.email.EmailDetails;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    private static final String SENDER = "noreply@example.com";

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    void sendsPlainTextBodyWithVariables() throws Exception {
        givenEmptyMimeMessage();

        service().sendMail(new EmailDetails(
                "recipient@example.com",
                "Email Verification",
                "Verify your account",
                Map.of("firstName", "John", "verificationUrl", "http://localhost/verify")));

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Email Verification");
        assertThat(message.getAllRecipients()[0]).hasToString("recipient@example.com");
        assertThat(message.getContent().toString())
                .contains("Verify your account")
                .contains("firstName: John")
                .contains("verificationUrl: http://localhost/verify");
    }

    @Test
    void skipsBlankVariables() throws Exception {
        givenEmptyMimeMessage();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("firstName", "John");
        variables.put("lastName", null);
        variables.put("userAgent", "   ");

        service().sendMail(new EmailDetails(
                "recipient@example.com", "Password Changed", null, variables));

        assertThat(sentMessage().getContent().toString()).isEqualTo("firstName: John");
    }

    @Test
    void usesSubjectWhenBodyAndVariablesAreEmpty() throws Exception {
        givenEmptyMimeMessage();

        service().sendMail(new EmailDetails(
                "recipient@example.com", "Account Locked", null, Map.of()));

        assertThat(sentMessage().getContent().toString()).isEqualTo("Account Locked");
    }

    @Test
    void rendersHtmlTemplateWithVerificationLink() throws Exception {
        givenEmptyMimeMessage();

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        templateEngine.setTemplateResolver(resolver);

        EmailSenderService service =
                new EmailSenderService(javaMailSender, SENDER, templateEngine);

        service.sendMail(new EmailDetails(
                "recipient@example.com",
                "Email Verification",
                "Verify your account",
                Map.of(
                        "firstName", "John",
                        "lastName", "Doe",
                        "username", "jdoe",
                        "email", "jdoe@example.com",
                        "verificationUrl", "http://localhost/verify")));

        String html = sentMessage().getContent().toString();
        assertThat(html).contains("<html")
                .contains("Email Verification")
                .contains("http://localhost/verify")
                .contains("Hello <strong>John</strong>")
                .contains("jdoe")
                .contains("John Doe")
                .contains("jdoe@example.com")
                .contains("15 minutes");
    }

    @Test
    void failsClearlyWhenSenderNotConfigured() {
        EmailSenderService service = new EmailSenderService(javaMailSender, "  ");

        assertThatThrownBy(() -> service.sendMail(new EmailDetails(
                "recipient@example.com", "Subject", null, Map.of())))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("spring.mail.username");

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void wrapsTransportFailureInMailSendException() {
        givenEmptyMimeMessage();
        doThrow(new MailAuthenticationException("smtp down"))
                .when(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service().sendMail(new EmailDetails(
                "recipient@example.com", "Subject", null, Map.of())))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("Failed to send email to recipient");
    }

    private void givenEmptyMimeMessage() {
        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    private EmailSenderService service() {
        return new EmailSenderService(javaMailSender, SENDER);
    }
}