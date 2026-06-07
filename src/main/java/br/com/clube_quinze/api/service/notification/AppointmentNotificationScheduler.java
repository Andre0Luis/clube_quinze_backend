package br.com.clube_quinze.api.service.notification;

import br.com.clube_quinze.api.config.RabbitMQConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.model.notification.AppointmentReminderLog;
import br.com.clube_quinze.api.repository.AppointmentReminderLogRepository;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.service.settings.SettingsService;
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
    private final UserRepository userRepository;
    private final SettingsService settingsService;

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
            RabbitTemplate rabbitTemplate,
            UserRepository userRepository,
            SettingsService settingsService) {
        this.appointmentRepository = appointmentRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
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
                // Idempotência: pula se este (agendamento, offset, CLIENT) já foi enfileirado.
                if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                        a.getId(), offsetMinutes, "CLIENT")) {
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
                    reminderLogRepository.save(new AppointmentReminderLog(a.getId(), offsetMinutes, "CLIENT"));
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

        // ── Lembretes para o ADMIN (configuráveis via painel) ────────────────────
        scanAndSendAdminReminders(now);
    }

    /**
     * Envia lembretes para todos os admins (CLUB_ADMIN) antes de cada atendimento,
     * nos offsets configurados no painel (tabela app_settings). Mesma janela de
     * catch-up e idempotência (recipient_type = ADMIN) do fluxo do cliente.
     */
    private void scanAndSendAdminReminders(LocalDateTime now) {
        if (!settingsService.isAdminReminderEnabled()) {
            return;
        }
        List<Integer> offsets = settingsService.getAdminReminderOffsets();
        if (offsets.isEmpty()) {
            return;
        }

        List<User> admins = userRepository.findByRole(RoleType.CLUB_ADMIN);
        if (admins.isEmpty()) {
            return;
        }

        for (int offsetMinutes : offsets) {
            LocalDateTime apptStart = now.plusMinutes(offsetMinutes - GRACE_MINUTES);
            LocalDateTime apptEnd = now.plusMinutes(offsetMinutes);

            List<Appointment> appts = appointmentRepository.findByStatusAndBetween(
                    AppointmentStatus.SCHEDULED, apptStart, apptEnd);
            if (appts.isEmpty()) continue;

            int enqueued = 0;
            for (Appointment a : appts) {
                if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                        a.getId(), offsetMinutes, "ADMIN")) {
                    continue;
                }

                User client = a.getClient();
                String clientName = client != null ? client.getName() : "Cliente";
                String formattedDate = a.getScheduledAt().format(PT_BR_FORMATTER);
                String label = humanizeOffset(offsetMinutes);
                String title = "Lembrete de atendimento";
                String body = "Próximo atendimento " + label + ": " + clientName + " às " + formattedDate;

                for (User admin : admins) {
                    Map<String, Object> pushData = new HashMap<>();
                    pushData.put("userId", admin.getId());
                    pushData.put("type", "ADMIN_REMINDER");
                    pushData.put("title", title);
                    pushData.put("body", body);
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("kind", "admin_reminder");
                    extra.put("appointmentId", a.getId());
                    pushData.put("data", extra);
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.NOTIFICATION_EXCHANGE,
                            RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                            new NotificationMessageDTO("PUSH_MESSAGE", pushData)
                    );
                }

                try {
                    reminderLogRepository.save(new AppointmentReminderLog(a.getId(), offsetMinutes, "ADMIN"));
                } catch (Exception ex) {
                    log.warn("Falha ao registrar log de lembrete ADMIN (appt={}, offset={}): {}",
                            a.getId(), offsetMinutes, ex.getMessage());
                }
                enqueued++;
            }
            if (enqueued > 0) {
                log.info("Enfileirados lembretes ADMIN para {} atendimento(s), offset {} min",
                        enqueued, offsetMinutes);
            }
        }
    }

    /** Converte minutos em rótulo amigável: 60→"em 1 hora", 90→"em 1h30", 30→"em 30 minutos". */
    private String humanizeOffset(int minutes) {
        if (minutes % 60 == 0) {
            int h = minutes / 60;
            return "em " + h + (h == 1 ? " hora" : " horas");
        }
        if (minutes > 60) {
            return "em " + (minutes / 60) + "h" + String.format("%02d", minutes % 60);
        }
        return "em " + minutes + " minutos";
    }
}
