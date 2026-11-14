package br.com.clube_quinze.api.service.notification;

import br.com.clube_quinze.api.config.RabbitMQConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import br.com.clube_quinze.api.dto.settings.AppointmentConfirmationSettings;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.model.notification.AppointmentReminderLog;
import br.com.clube_quinze.api.repository.AppointmentReminderLogRepository;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.config.AppConfig;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import br.com.clube_quinze.api.service.settings.SettingsService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AppointmentNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationScheduler.class);
    private static final DateTimeFormatter PT_BR_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    /** Tipos de destinatário gravados no log de idempotência (coluna com limite de 20 caracteres). */
    static final String RECIPIENT_CLIENT = "CLIENT";
    static final String RECIPIENT_CLIENT_CONFIRM = "CLIENT_CONFIRM";
    static final String RECIPIENT_ADMIN = "ADMIN";
    static final String RECIPIENT_ADMIN_UNCONFIRMED = "ADMIN_UNCONFIRMED";

    /** Agendamentos que ainda vão acontecer e, portanto, recebem lembretes. */
    private static final List<AppointmentStatus> REMINDABLE_STATUSES = List.of(
            AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED, AppointmentStatus.RESCHEDULE_REQUESTED);

    // Catch-up: tolera ticks perdidos (deploy/restart/VPS lento) sem perder o lembrete.
    // Maior que a janela de 1 min original; combinado com o log de idempotência, nunca duplica.
    private static final int GRACE_MINUTES = 10;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentReminderLogRepository reminderLogRepository;
    private final RabbitTemplate rabbitTemplate;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final AppointmentActionTokenService tokenService;
    private final String publicBaseUrl;

    public AppointmentNotificationScheduler(
            AppointmentRepository appointmentRepository,
            AppointmentReminderLogRepository reminderLogRepository,
            RabbitTemplate rabbitTemplate,
            UserRepository userRepository,
            SettingsService settingsService,
            AppointmentActionTokenService tokenService,
            @Value("${app.public.base-url}") String publicBaseUrl) {
        this.appointmentRepository = appointmentRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    // every 1 minute to guarantee 30min window is not missed
    @Scheduled(cron = "0 */1 * * * *")
    public void scanAndSendReminders() {
        // "Agora" no fuso de Brasília, para casar com o scheduledAt (wall-clock de Brasília).
        LocalDateTime now = LocalDateTime.now(AppConfig.APP_ZONE);
        AppointmentConfirmationSettings cfg = settingsService.getAppointmentConfirmationSettings();

        sendClientReminders(now, cfg);
        if (Boolean.TRUE.equals(cfg.enabled())) {
            sendConfirmationRequests(now, cfg);
            alertAdminAboutUnconfirmed(now, cfg);
        }

        // ── Lembretes para o ADMIN (configuráveis via painel) ────────────────────
        scanAndSendAdminReminders(now);
    }

    /** Janela de varredura: agendamentos cujo "horário de lembrete" (scheduledAt - offset) ocorreu nos últimos GRACE min. */
    private List<Appointment> findDue(LocalDateTime now, int offsetMinutes, List<AppointmentStatus> statuses) {
        return appointmentRepository.findByStatusInAndBetween(
                statuses,
                now.plusMinutes(offsetMinutes - GRACE_MINUTES),
                now.plusMinutes(offsetMinutes));
    }

    // ── Lembretes simples ao cliente (24h, 3h, 1h, 30min por padrão) ──────────

    private void sendClientReminders(LocalDateTime now, AppointmentConfirmationSettings cfg) {
        for (int offsetMinutes : cfg.clientReminderOffsets()) {
            String offsetLabel = clientOffsetLabel(offsetMinutes);
            List<Appointment> appts = findDue(now, offsetMinutes, REMINDABLE_STATUSES);
            if (appts.isEmpty()) continue;

            int enqueued = 0;
            for (Appointment a : appts) {
                // Idempotência: pula se este (agendamento, offset, CLIENT) já foi enfileirado.
                if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                        a.getId(), offsetMinutes, RECIPIENT_CLIENT)) {
                    continue;
                }
                // Cliente já confirmou: lembretes configurados em "skipRemindersWhenConfirmed" são dispensados.
                if (a.getStatus() == AppointmentStatus.CONFIRMED
                        && cfg.skipRemindersWhenConfirmed().contains(offsetMinutes)) {
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

                markSent(a.getId(), offsetMinutes, RECIPIENT_CLIENT);
                enqueued++;
            }
            if (enqueued > 0) {
                log.info("Enfileirados {} lembretes para offset {} min ({})", enqueued, offsetMinutes, offsetLabel);
            }
        }
    }

    // ── Pedido de confirmação ao cliente (1h30 antes por padrão) ──────────────

    private void sendConfirmationRequests(LocalDateTime now, AppointmentConfirmationSettings cfg) {
        int offsetMinutes = cfg.confirmationRequestOffset();
        List<Appointment> appts = findDue(now, offsetMinutes, List.of(AppointmentStatus.SCHEDULED));
        int enqueued = 0;
        for (Appointment a : appts) {
            if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                    a.getId(), offsetMinutes, RECIPIENT_CLIENT_CONFIRM)) {
                continue;
            }
            // Marcado em cima da hora (depois do momento do pedido): já está combinado, não pede confirmação.
            if (createdAfterRequestMoment(a, offsetMinutes)) {
                continue;
            }

            User client = a.getClient();
            String formattedDate = a.getScheduledAt().format(PT_BR_FORMATTER);
            Instant expiresAt = a.getScheduledAt().atZone(AppConfig.APP_ZONE).toInstant();
            String confirmUrl = actionUrl(a.getId(), Action.CONFIRM, expiresAt);
            String cancelUrl = actionUrl(a.getId(), Action.CANCEL, expiresAt);
            String rescheduleUrl = actionUrl(a.getId(), Action.RESCHEDULE, expiresAt);

            // Push: o app abre a tela de confirmação (kind=appointment_confirmation); iOS mostra ações rápidas.
            Map<String, Object> extra = new HashMap<>();
            extra.put("kind", "appointment_confirmation");
            extra.put("appointmentId", a.getId());
            extra.put("scheduledAt", a.getScheduledAt().toString());
            extra.put("categoryId", "APPOINTMENT_CONFIRMATION");
            extra.put("actions", "confirm,cancel,reschedule");
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("userId", client.getId());
            pushData.put("type", "APPOINTMENT_CONFIRMATION");
            pushData.put("title", "Confirme seu agendamento");
            pushData.put("body", "Seu atendimento é " + humanizeOffset(offsetMinutes) + " (" + formattedDate
                    + "). Toque para confirmar, remarcar ou cancelar.");
            pushData.put("data", extra);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    new NotificationMessageDTO("PUSH_MESSAGE", pushData));

            // E-mail com os botões (links assinados)
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("email", client.getEmail());
            emailData.put("name", client.getName());
            emailData.put("scheduledAt", formattedDate);
            emailData.put("description", a.getNotes() != null ? a.getNotes() : "");
            emailData.put("confirmUrl", confirmUrl);
            emailData.put("cancelUrl", cancelUrl);
            emailData.put("rescheduleUrl", rescheduleUrl);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    new NotificationMessageDTO("APPOINTMENT_CONFIRMATION_EMAIL", emailData));

            markSent(a.getId(), offsetMinutes, RECIPIENT_CLIENT_CONFIRM);
            enqueued++;
        }
        if (enqueued > 0) {
            log.info("Enfileirados {} pedidos de confirmação (offset {} min)", enqueued, offsetMinutes);
        }
    }

    // ── Aviso ao admin: cliente não confirmou (1h antes por padrão) ───────────

    private void alertAdminAboutUnconfirmed(LocalDateTime now, AppointmentConfirmationSettings cfg) {
        int alertOffset = cfg.adminAlertOffset();
        List<Appointment> appts = findDue(now, alertOffset, List.of(AppointmentStatus.SCHEDULED));
        if (appts.isEmpty()) return;

        List<User> admins = userRepository.findByRole(RoleType.CLUB_ADMIN);
        if (admins.isEmpty()) return;

        int enqueued = 0;
        for (Appointment a : appts) {
            if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                    a.getId(), alertOffset, RECIPIENT_ADMIN_UNCONFIRMED)) {
                continue;
            }
            // Só alerta se o cliente de fato recebeu o pedido de confirmação.
            if (!reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                    a.getId(), cfg.confirmationRequestOffset(), RECIPIENT_CLIENT_CONFIRM)) {
                continue;
            }

            String clientName = a.getClient() != null && a.getClient().getName() != null
                    ? a.getClient().getName() : "Cliente";
            String formattedDate = a.getScheduledAt().format(PT_BR_FORMATTER);
            String body = clientName + " ainda não confirmou o atendimento de " + formattedDate + " ("
                    + humanizeOffset(alertOffset) + "). Mantenha, cancele ou troque o horário.";

            for (User admin : admins) {
                Map<String, Object> extra = new HashMap<>();
                extra.put("kind", "admin_unconfirmed");
                extra.put("appointmentId", a.getId());
                extra.put("scheduledAt", a.getScheduledAt().toString());
                extra.put("actions", "keep,cancel,reschedule");
                Map<String, Object> pushData = new HashMap<>();
                pushData.put("userId", admin.getId());
                pushData.put("type", "ADMIN_APPOINTMENT_UNCONFIRMED");
                pushData.put("title", "Cliente não confirmou");
                pushData.put("body", body);
                pushData.put("data", extra);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.NOTIFICATION_EXCHANGE,
                        RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                        new NotificationMessageDTO("PUSH_MESSAGE", pushData));
            }
            markSent(a.getId(), alertOffset, RECIPIENT_ADMIN_UNCONFIRMED);
            enqueued++;
        }
        if (enqueued > 0) {
            log.info("Avisados admins sobre {} agendamento(s) sem confirmação (offset {} min)", enqueued, alertOffset);
        }
    }

    /** True se o agendamento foi criado depois do momento em que o pedido de confirmação seria enviado. */
    private boolean createdAfterRequestMoment(Appointment a, int offsetMinutes) {
        if (a.getCreatedAt() == null) return false;
        // createdAt é gravado no fuso padrão da JVM; converte para o fuso de Brasília antes de comparar.
        LocalDateTime createdInAppZone = a.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(AppConfig.APP_ZONE)
                .toLocalDateTime();
        return createdInAppZone.isAfter(a.getScheduledAt().minusMinutes(offsetMinutes));
    }

    private String actionUrl(Long appointmentId, Action action, Instant expiresAt) {
        return publicBaseUrl + "/appointment-action?token=" + tokenService.create(appointmentId, action, expiresAt);
    }

    /** Marca como enviado (idempotência). Best-effort: se falhar, não relança. */
    private void markSent(Long appointmentId, int offsetMinutes, String recipientType) {
        try {
            reminderLogRepository.save(new AppointmentReminderLog(appointmentId, offsetMinutes, recipientType));
        } catch (Exception ex) {
            log.warn("Falha ao registrar log de lembrete (appt={}, offset={}, tipo={}): {}",
                    appointmentId, offsetMinutes, recipientType, ex.getMessage());
        }
    }

    /** Mantém os rótulos originais dos lembretes ("amanhã" para 24h). */
    private String clientOffsetLabel(int minutes) {
        return minutes == 24 * 60 ? "amanhã" : humanizeOffset(minutes);
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
            List<Appointment> appts = findDue(now, offsetMinutes, REMINDABLE_STATUSES);
            if (appts.isEmpty()) continue;

            int enqueued = 0;
            for (Appointment a : appts) {
                if (reminderLogRepository.existsByAppointmentIdAndOffsetMinutesAndRecipientType(
                        a.getId(), offsetMinutes, RECIPIENT_ADMIN)) {
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

                markSent(a.getId(), offsetMinutes, RECIPIENT_ADMIN);
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
