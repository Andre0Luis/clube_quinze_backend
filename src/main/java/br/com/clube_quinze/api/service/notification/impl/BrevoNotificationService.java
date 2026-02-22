package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@ConditionalOnProperty(prefix = "app.brevo", name = "enabled", havingValue = "true")
public class BrevoNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(BrevoNotificationService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final TemplateEngine templateEngine;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String fromName;
    private final String fromEmail;

    public BrevoNotificationService(TemplateEngine templateEngine,
                                    @Value("${app.mail.from-name:Clube Quinze}") String fromName,
                                    @Value("${app.mail.from:no-reply@clubequinzeapp.cloud}") String fromEmail,
                                    @Value("${app.brevo.api-key}") String apiKey,
                                    @Value("${app.brevo.base-url:https://api.brevo.com}") String baseUrl) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("app.brevo.api-key must be set when Brevo is enabled");
        }
        this.templateEngine = templateEngine;
        this.fromName = fromName;
        this.fromEmail = fromEmail;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Async("asyncExecutor")
    public void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating) {
        log.info("[async-brevo] Feedback user={} appointment={} rating={}", userId, appointmentId, rating);
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
        String url = baseUrl + "/v3/smtp/email";
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("name", StringUtils.hasText(toName) ? toName : toEmail, "email", toEmail)),
                "subject", subject,
                "htmlContent", html,
                "textContent", toPlainText(html)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
            log.info("[async-brevo] enviado para {}", toEmail);
        } catch (RestClientException ex) {
            log.error("[async-brevo] falha ao enviar para {}: {}", toEmail, ex.getMessage(), ex);
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