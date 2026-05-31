package br.com.clube_quinze.api.service.notification;

import br.com.clube_quinze.api.config.RabbitMQConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.model.notification.AppointmentReminderLog;
import br.com.clube_quinze.api.repository.AppointmentReminderLogRepository;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationScheduler.class);
    private static final DateTimeFormatter PT_BR_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private final AppointmentRepository appointmentRepository;
    private final AppointmentReminderLogRepository reminderLogRepository;
    private final RabbitTemplate rabbitTemplate;

    // Reminder config: offset in minutes and user-friendly label
    private static final int[][] REMINDER_OFFSETS = {
            {24 * 60, 0},  // 24h
            {3 * 60, 1},   // 3h
            {60, 2},       // 1h
            {30, 3}        // 30min
    };
    private static final String[] OFFSET_LABELS = {
            "amanhã", "em 3 horas", "em 1 hora", "em 30 minutos"
    };

    // Catch-up: tolera ticks perdidos (deploy/restart/VPS lento) sem perder o lembrete.
    // Maior que a janela de 1 min original; combinado com o log de idempotência, nunca duplica.
    private static final int GRACE_MINUTES = 10;

    public AppointmentNotificationScheduler(
            AppointmentRepository appointmentRepository,
            AppointmentReminderLogRepository reminderLogRepository,
            RabbitTemplate rabbitTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    // every 1 minute to guarantee 30min window is not missed
    @Scheduled(cron = "0 */1 * * * *")
    public void scanAndSendReminders() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        for (int i = 0; i < REMINDER_OFFSETS.length; i++) {
            int offsetMinutes = REMINDER_OFFSETS[i][0];
            int labelIndex = REMINDER_OFFSETS[i][1];
            String offsetLabel = OFFSET_LABELS[labelIndex];

            // Janela de varredura: agendamentos cujo "horário de lembrete" (scheduledAt - offset)
            // ocorreu nos últimos GRACE minutos. Assim um tick perdido (deploy/VPS lento) é recuperado,
            // e a idempotência (reminderLogRepository) impede qualquer duplicação.
            LocalDateTime apptStart = now.plusMinutes(offsetMinutes - GRACE_MINUTES);
            LocalDateTime apptEnd = now.plusMinutes(offsetMinutes);

            List<Appointment> appts = appointmentRepository.findByStatusAndBetween(
                    AppointmentStatus.SCHEDULED, apptStart, apptEnd);
            if (appts.isEmpty()) continue;

            int enqueued = 0;
            for (Appointment a : appts) {
                // Idempotência: pula se este (agendamento, offset) já foi enfileirado.
                if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutes(a.getId(), offsetMinutes)) {
                    continue;
                }

                User client = a.getClient();
                String formattedDate = a.getScheduledAt().format(PT_BR_FORMATTER);
                String description = a.getNotes() != null ? a.getNotes() : "";

                // 1) Push notification Queue
                Map<String, Object> pushData = new HashMap<>();
                pushData.put("userId", client.getId());
                pushData.put("appointmentId", a.getId());
                pushData.put("formattedDate", formattedDate);
                pushData.put("offsetLabel", offsetLabel);
                pushData.put("offsetMinutes", offsetMinutes);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.NOTIFICATION_EXCHANGE,
                        RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                        new NotificationMessageDTO("APPOINTMENT_REMINDER_PUSH", pushData)
                );

                // 2) Email notification Queue
                Map<String, Object> emailData = new HashMap<>();
                emailData.put("email", client.getEmail());
                emailData.put("name", client.getName());
                emailData.put("scheduledAt", formattedDate);
                emailData.put("description", description);
                emailData.put("offsetLabel", offsetLabel);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.NOTIFICATION_EXCHANGE,
                        RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                        new NotificationMessageDTO("APPOINTMENT_REMINDER_EMAIL", emailData)
                );

                // Marca como enviado (idempotência). Best-effort: se falhar, não relança.
                try {
                    reminderLogRepository.save(new AppointmentReminderLog(a.getId(), offsetMinutes));
                } catch (Exception ex) {
                    log.warn("Falha ao registrar log de lembrete (appt={}, offset={}): {}",
                            a.getId(), offsetMinutes, ex.getMessage());
                }
                enqueued++;
            }
            if (enqueued > 0) {
                log.info("Enfileirados {} lembretes para offset {} min ({})", enqueued, offsetMinutes, offsetLabel);
            }
        }
    }
}
