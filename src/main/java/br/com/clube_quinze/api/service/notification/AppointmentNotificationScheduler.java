package br.com.clube_quinze.api.service.notification;

import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationScheduler.class);
    private final AppointmentRepository appointmentRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ExpoPushService expoPushService;

    public AppointmentNotificationScheduler(
            AppointmentRepository appointmentRepository,
            PushTokenRepository pushTokenRepository,
            ExpoPushService expoPushService) {
        this.appointmentRepository = appointmentRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.expoPushService = expoPushService;
    }

    // every 5 minutes
    @Scheduled(cron = "0 */5 * * * *")
    public void scanAndSendReminders() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // look ahead a bit to catch windows; we'll compute target scheduledAt windows where notification time falls between now and now+5min
        LocalDateTime windowEnd = now.plusMinutes(5);

        // For each offset compute appointments where scheduledAt - offset is in (now, windowEnd]
        int[] offsetsHours = {24, 6, 3, 1};
        for (int h : offsetsHours) {
            LocalDateTime start = now.plusHours(h * -1).plusMinutes(0); // scheduledAt - h == now => scheduledAt == now + h
            LocalDateTime searchStart = now.plusSeconds(1);
            LocalDateTime searchEnd = windowEnd.plusHours(h);
            // Simpler: find appointments between now+h and windowEnd+h
            LocalDateTime apptStart = now.plusHours(h);
            LocalDateTime apptEnd = windowEnd.plusHours(h);
            List<Appointment> appts = appointmentRepository.findByScheduledAtBetween(apptStart, apptEnd);
            if (appts.isEmpty()) continue;
            log.info("Found {} appointments for offset {}h", appts.size(), h);
            for (Appointment a : appts) {
                if (a.getStatus() != AppointmentStatus.SCHEDULED) continue;
                Long userId = a.getClient().getId();
                List<PushToken> tokens = pushTokenRepository.findByUserIdAndInvalidatedAtIsNull(userId);
                if (tokens.isEmpty()) continue;
                String title = "Lembrete de agendamento";
                String body = String.format("Você tem um agendamento em %s (em %dh)", a.getScheduledAt().toString(), h);
                Map<String, Object> data = new HashMap<>();
                data.put("appointmentId", a.getId());
                data.put("kind", "reminder");
                data.put("offset", "-" + h + "h");
                List<ExpoPushService.ExpoMessage> messages = new ArrayList<>();
                for (PushToken t : tokens) {
                    messages.add(new ExpoPushService.ExpoMessage(t.getToken(), title, body, data));
                }
                // batch send in groups of up to 100
                int batchSize = 100;
                for (int i = 0; i < messages.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, messages.size());
                    List<ExpoPushService.ExpoMessage> batch = messages.subList(i, end);
                    var results = expoPushService.sendBatch(batch);
                    // basic handling: mark tokens invalid if error status indicates device not registered
                    for (int j = 0; j < results.size(); j++) {
                        var r = results.get(j);
                        if (!r.ok()) {
                            // log and optionally mark invalid; real parsing needed for detailed reasons
                            log.warn("Push failed: status={} msg={}", r.status(), r.message());
                        }
                    }
                }
            }
        }
    }
}
