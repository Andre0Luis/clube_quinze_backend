package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.NotificationService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@ConditionalOnProperty(prefix = "app.mailer-send", name = "enabled", havingValue = "false", matchIfMissing = true)
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final TemplateEngine templateEngine;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${app.mail.from:no-reply@clubequinzeapp.cloud}") String from,
                                    TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.from = from;
        this.templateEngine = templateEngine;
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
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("email", email);
        context.setVariable("rawPassword", rawPassword);
        String html = templateEngine.process("welcome", context);
        String subject = "Bem-vindo ao Clube Quinze";
        try {
            sendHtml(email, subject, html);
            log.info("[async-email] Bem-vindo enviado para {}", email);
        } catch (MailException ex) {
            log.error("Falha ao enviar email de boas-vindas para {}: {}", email, ex.getMessage());
        }
    }

    @Override
    @Async("asyncExecutor")
    public void notifyPasswordReset(String email, String name, String resetLink) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("resetLink", resetLink);
        String html = templateEngine.process("forgot-passworld", context);
        String subject = "Recuperacao de senha";
        try {
            sendHtml(email, subject, html);
            log.info("[async-email] Reset de senha enviado para {}", email);
        } catch (MailException ex) {
            log.error("Falha ao enviar reset de senha para {}: {}", email, ex.getMessage());
        }
    }

    private void sendHtml(String to, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new MailException("Falha ao montar email HTML") {
                private static final long serialVersionUID = 1L;
            };
        }
    }
}
