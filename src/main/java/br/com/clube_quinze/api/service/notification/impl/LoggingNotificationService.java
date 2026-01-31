package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
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
}
