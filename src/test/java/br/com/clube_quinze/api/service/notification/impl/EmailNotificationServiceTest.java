package br.com.clube_quinze.api.service.notification.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    private EmailNotificationService subject;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));
        subject = new EmailNotificationService(mailSender, "no-reply@clubequinzeapp.cloud", templateEngine);
    }

    @Test
    void notifyWelcome_rendersTemplateAndSendsEmail() throws Exception {
        when(templateEngine.process(eq("welcome"), any(Context.class))).thenReturn("<html>Boas-vindas</html>");

        subject.notifyWelcome("user@example.com", "André", "senha123");

        verify(mailSender).send(mimeMessage);
        assertEquals("Bem-vindo ao Clube Quinze", mimeMessage.getSubject());
        assertArrayEquals(new InternetAddress[] { new InternetAddress("user@example.com") }, mimeMessage.getRecipients(Message.RecipientType.TO));

        mimeMessage.saveChanges();
        assertEquals("<html>Boas-vindas</html>", mimeMessage.getContent().toString().trim());

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("welcome"), contextCaptor.capture());
        Context captured = contextCaptor.getValue();
        assertEquals("André", captured.getVariable("name"));
        assertEquals("user@example.com", captured.getVariable("email"));
        assertEquals("senha123", captured.getVariable("rawPassword"));
    }

    @Test
    void notifyPasswordReset_rendersTemplateAndSendsEmail() throws Exception {
        when(templateEngine.process(eq("forgot-passworld"), any(Context.class))).thenReturn("<html>Redefinir</html>");

        subject.notifyPasswordReset("user@example.com", "André", "https://link/reset?token=abc");

        verify(mailSender).send(mimeMessage);
        assertEquals("Recuperacao de senha", mimeMessage.getSubject());
        assertArrayEquals(new InternetAddress[] { new InternetAddress("user@example.com") }, mimeMessage.getRecipients(Message.RecipientType.TO));

        mimeMessage.saveChanges();
        assertEquals("<html>Redefinir</html>", mimeMessage.getContent().toString().trim());

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("forgot-passworld"), contextCaptor.capture());
        Context captured = contextCaptor.getValue();
        assertEquals("André", captured.getVariable("name"));
        assertEquals("https://link/reset?token=abc", captured.getVariable("resetLink"));
    }
}
