package br.com.clube_quinze.api.service.notification;

import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationScheduler.class);
    private static final DateTimeFormatter PT_BR_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private final AppointmentRepository appointmentRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ExpoPushService expoPushService;
    private final NotificationService notificationService;

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

    public AppointmentNotificationScheduler(
            AppointmentRepository appointmentRepository,
            PushTokenRepository pushTokenRepository,
            ExpoPushService expoPushService,
            NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.expoPushService = expoPushService;
        this.notificationService = notificationService;
    }

    // every 1 minute to guarantee 30min window is not missed
    @Scheduled(cron = "0 */1 * * * *")
    public void scanAndSendReminders() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime windowEnd = now.plusMinutes(1);

        for (int i = 0; i < REMINDER_OFFSETS.length; i++) {
            int offsetMinutes = REMINDER_OFFSETS[i][0];
            int labelIndex = REMINDER_OFFSETS[i][1];
            String offsetLabel = OFFSET_LABELS[labelIndex];

            // Find appointments whose scheduledAt is between now+offset and windowEnd+offset
            LocalDateTime apptStart = now.plusMinutes(offsetMinutes);
            LocalDateTime apptEnd = windowEnd.plusMinutes(offsetMinutes);

            List<Appointment> appts = appointmentRepository.findByStatusAndBetween(
                    AppointmentStatus.SCHEDULED, apptStart, apptEnd);
            if (appts.isEmpty()) continue;

            log.info("Found {} appointments for offset {} min ({})", appts.size(), offsetMinutes, offsetLabel);

            for (Appointment a : appts) {
                User client = a.getClient();
                Long userId = client.getId();
                String formattedDate = a.getScheduledAt().format(PT_BR_FORMATTER);
                String description = a.getNotes() != null ? a.getNotes() : "";

                // 1) Push notification
                sendPushReminder(a, userId, formattedDate, offsetLabel, offsetMinutes);

                // 2) Email notification
                try {
                    notificationService.notifyAppointmentReminder(
                            client.getEmail(), client.getName(),
                            formattedDate, description, offsetLabel);
                } catch (Exception ex) {
                    log.error("Falha ao enviar email de lembrete para user={}: {}", userId, ex.getMessage());
                }
            }
        }
    }

    private void sendPushReminder(Appointment a, Long userId, String formattedDate, String offsetLabel, int offsetMinutes) {
        List<PushToken> tokens = pushTokenRepository.findByUserIdAndInvalidatedAtIsNull(userId);
        if (tokens.isEmpty()) return;

        String title = "Lembrete de agendamento";
        String body = String.format("Você tem um agendamento %s — %s", offsetLabel, formattedDate);
        Map<String, Object> data = new HashMap<>();
        data.put("appointmentId", a.getId());
        data.put("kind", "reminder");
        data.put("offset", offsetMinutes >= 60 ? "-" + (offsetMinutes / 60) + "h" : "-" + offsetMinutes + "min");

        List<ExpoPushService.ExpoMessage> messages = new ArrayList<>();
        for (PushToken t : tokens) {
            messages.add(new ExpoPushService.ExpoMessage(t.getToken(), title, body, data));
        }

        int batchSize = 100;
        for (int i = 0; i < messages.size(); i += batchSize) {
            int end = Math.min(i + batchSize, messages.size());
            List<ExpoPushService.ExpoMessage> batch = messages.subList(i, end);
            var results = expoPushService.sendBatch(batch);
            for (int j = 0; j < results.size(); j++) {
                var r = results.get(j);
                if (!r.ok()) {
                    log.warn("Push failed: status={} msg={}", r.status(), r.message());
                }
            }
        }
    }
}
