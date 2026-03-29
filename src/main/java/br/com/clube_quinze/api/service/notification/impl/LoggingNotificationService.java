package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.notifications.logging", name = "enabled", havingValue = "true")
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    @Override
    @Async("asyncExecutor")
    public void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating) {
        // Placeholder for future email/push integration
        log.info("[async] Feedback recebido user={} appointment={} rating={}", userId, appointmentId, rating);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyWelcome(String email, String name, String rawPassword) {
        log.info("[async] Bem-vindo enviado para email={} name={} (senha não logada)", email, name);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyPasswordReset(String email, String name, String resetLink) {
        log.info("[async] Reset de senha enviado para email={} name={}", email, name);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyAppointmentReminder(String email, String name, String scheduledAt, String description, String offsetLabel) {
        log.info("[async] Lembrete de agendamento para email={} name={} em={} offset={}", email, name, scheduledAt, offsetLabel);
    }

    @Override
    @Async("asyncExecutor")
    public void notifyAppointmentRescheduled(String email, String name, String oldScheduledAt, String newScheduledAt, String description) {
        log.info("[async] Agendamento remarcado para email={} name={} de={} para={}", email, name, oldScheduledAt, newScheduledAt);
    }
}
