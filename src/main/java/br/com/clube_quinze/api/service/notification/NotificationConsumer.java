package br.com.clube_quinze.api.service.notification;

import br.com.clube_quinze.api.config.RabbitMQConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;

    public NotificationConsumer(NotificationService notificationService,
                                PushNotificationService pushNotificationService) {
        this.notificationService = notificationService;
        this.pushNotificationService = pushNotificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consumeMessage(NotificationMessageDTO message) {
        log.info("Recebido na Fila (RabbitMQ): {}", message.getType());
        
        try {
            switch (message.getType()) {
                case "WELCOME_EMAIL":
                    handleWelcomeEmail(message.getData());
                    break;
                case "PASSWORD_RESET_EMAIL":
                    handlePasswordResetEmail(message.getData());
                    break;
                case "FEEDBACK_RECEIVED":
                    handleFeedbackReceived(message.getData());
                    break;
                case "APPOINTMENT_REMINDER_EMAIL":
                    handleAppointmentReminderEmail(message.getData());
                    break;
                case "APPOINTMENT_RESCHEDULED_EMAIL":
                    handleAppointmentRescheduledEmail(message.getData());
                    break;
                case "PUSH_MESSAGE":
                case "APPOINTMENT_REMINDER_PUSH":
                    handlePushMessage(message.getData());
                    break;
                default:
                    log.warn("Tipo de notificação desconhecido: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Erro ao processar notificação {}: {}", message.getType(), e.getMessage());
        }
    }

    private void handleWelcomeEmail(Map<String, Object> data) {
        notificationService.notifyWelcome(
                getString(data, "email"),
                getString(data, "name"),
                getString(data, "rawPassword")
        );
    }

    private void handlePasswordResetEmail(Map<String, Object> data) {
        notificationService.notifyPasswordReset(
                getString(data, "email"),
                getString(data, "name"),
                getString(data, "resetLink")
        );
    }

    private void handleFeedbackReceived(Map<String, Object> data) {
        notificationService.notifyFeedbackReceived(
                getLong(data, "userId"),
                getLong(data, "appointmentId"),
                getInteger(data, "rating")
        );
    }

    private void handleAppointmentReminderEmail(Map<String, Object> data) {
        notificationService.notifyAppointmentReminder(
                getString(data, "email"),
                getString(data, "name"),
                getString(data, "scheduledAt"),
                getString(data, "description"),
                getString(data, "offsetLabel")
        );
    }

    private void handleAppointmentRescheduledEmail(Map<String, Object> data) {
        notificationService.notifyAppointmentRescheduled(
                getString(data, "email"),
                getString(data, "name"),
                getString(data, "oldScheduledAt"),
                getString(data, "newScheduledAt"),
                getString(data, "description")
        );
    }

    @SuppressWarnings("unchecked")
    private void handlePushMessage(Map<String, Object> data) {
        Long userId = getLong(data, "userId");
        String type = getString(data, "type");
        if (type == null) type = "REMINDER";
        
        String title = getString(data, "title");
        if (title == null) title = "Lembrete de agendamento";
        
        String body = getString(data, "body");
        if (body == null) body = "Você tem um agendamento " + getString(data, "offsetLabel") + " — " + getString(data, "formattedDate");

        Map<String, Object> extraData = (Map<String, Object>) data.get("data");
        if (extraData == null) {
            extraData = new java.util.HashMap<>();
            extraData.put("appointmentId", data.get("appointmentId"));
            if (data.containsKey("offsetMinutes")) {
                extraData.put("kind", "reminder");
                // The consumer might receive maps from JSON so integers are Long normally, but let's be safe:
                Object offsetMinutes = data.get("offsetMinutes");
                if (offsetMinutes != null) {
                    try {
                        int o = Integer.parseInt(offsetMinutes.toString());
                        extraData.put("offset", o >= 60 ? "-" + (o / 60) + "h" : "-" + o + "min");
                    } catch (Exception ignored) {}
                }
            }
        }

        pushNotificationService.sendToUser(userId, type, title, body, extraData);
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private Long getLong(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    private Integer getInteger(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }
}
