package br.com.clube_quinze.api.service.google;

import br.com.clube_quinze.api.config.RabbitMQConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pede a sincronização de um agendamento com o Google. Publica na fila SOMENTE depois do commit (assim o consumidor
 * enxerga o dado salvo e nada vai se a transação der rollback). Nunca quebra o fluxo do agendamento: se falhar, o
 * reconciliador periódico corrige em poucos minutos.
 */
@Component
public class GoogleCalendarSyncTrigger {

    public static final String MESSAGE_TYPE = "GOOGLE_CALENDAR_SYNC";
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarSyncTrigger.class);

    private final RabbitTemplate rabbitTemplate;
    private final GoogleCalendarService googleCalendarService;

    public GoogleCalendarSyncTrigger(RabbitTemplate rabbitTemplate, GoogleCalendarService googleCalendarService) {
        this.rabbitTemplate = rabbitTemplate;
        this.googleCalendarService = googleCalendarService;
    }

    public void requestSync(Long appointmentId) {
        try {
            if (appointmentId == null || !googleCalendarService.isSyncActive()) {
                return;
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish(appointmentId);
                    }
                });
            } else {
                publish(appointmentId);
            }
        } catch (RuntimeException ex) {
            log.warn("Não foi possível pedir a sincronização do agendamento {} com o Google: {}", appointmentId, ex.getMessage());
        }
    }

    private void publish(Long appointmentId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("appointmentId", appointmentId);
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    new NotificationMessageDTO(MESSAGE_TYPE, data));
        } catch (RuntimeException ex) {
            log.warn("Fila indisponível ao pedir a sincronização do agendamento {} (o reconciliador cobre): {}", appointmentId, ex.getMessage());
        }
    }
}
