package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Primary
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${app.mail.from:no-reply@clubequinzeapp.cloud}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    @Async("asyncExecutor")
    public void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating) {
        // Placeholder: could send email/push to staff. For now, just log to avoid spurious emails.
        log.info("[async-email] Feedback recebido user={} appointment={} rating={}", userId, appointmentId, rating);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyWelcome(String email, String name, String rawPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(from);
        message.setSubject("Bem-vindo ao Clube Quinze");
        message.setText(buildWelcomeBody(name, email, rawPassword));
        try {
            mailSender.send(message);
            log.info("[async-email] Bem-vindo enviado para {}", email);
        } catch (MailException ex) {
            log.error("Falha ao enviar email de boas-vindas para {}: {}", email, ex.getMessage());
        }
    }

    private String buildWelcomeBody(String name, String email, String rawPassword) {
        return "Olá " + (name != null ? name : "") + ",\n\n" +
                "Bem-vindo ao Clube Quinze!\n" +
                "Seu acesso:\n" +
                "- Email: " + email + "\n" +
                "- Senha: " + rawPassword + "\n\n" +
                "Recomendamos alterar a senha após o primeiro login.\n\n" +
                "Equipe Clube Quinze";
    }
}
