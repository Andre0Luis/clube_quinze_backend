package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.NotificationService;
import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.MailerSendResponse;
import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.exceptions.MailerSendException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@ConditionalOnProperty(prefix = "app.mailer-send", name = "enabled", havingValue = "true")
public class MailerSendNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailerSendNotificationService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final TemplateEngine templateEngine;
    private final MailerSend mailerSend;
    private final String fromName;
    private final String fromEmail;

    public MailerSendNotificationService(TemplateEngine templateEngine,
                                         @Value("${app.mailer-send.from-name:Clube Quinze}") String fromName,
                                         @Value("${app.mail.from:no-reply@clubequinzeapp.cloud}") String fromEmail,
                                         @Value("${app.mailer-send.token}") String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("app.mailer-send.token must be set when MailerSend is enabled");
        }
        this.templateEngine = templateEngine;
        this.fromName = fromName;
        this.fromEmail = fromEmail;
        this.mailerSend = new MailerSend();
        this.mailerSend.setToken(token);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating) {
        log.info("[async-mailersend] Feedback user={} appointment={} rating={}", userId, appointmentId, rating);
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
        sendEmail(email, name, subject, html);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyPasswordReset(String email, String name, String resetLink) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("resetLink", resetLink);
        String html = templateEngine.process("forgot-passworld", context);
        String subject = "Recuperacao de senha";
        sendEmail(email, name, subject, html);
    }

    private void sendEmail(String toEmail, String toName, String subject, String html) {
        Email email = new Email();
        email.setFrom(fromName, fromEmail);
        email.addRecipient(StringUtils.hasText(toName) ? toName : toEmail, toEmail);
        email.setSubject(subject);
        email.setHtml(html);
        email.setPlain(toPlainText(html));
        try {
            MailerSendResponse response = mailerSend.emails().send(email);
            log.info("[async-mailersend] enviado para {} (messageId={})", toEmail, response.messageId);
        } catch (MailerSendException ex) {
            log.error("[async-mailersend] falha ao enviar para {}: code={} message={}", toEmail, ex.code, ex.message, ex);
        }
    }

    private String toPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String plain = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        plain = plain.replaceAll("&nbsp;", " ");
        return plain.replaceAll("\\s+", " ").trim();
    }
}