package br.com.clube_quinze.api.integration.notification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.config.AppConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import br.com.clube_quinze.api.dto.settings.AppointmentConfirmationSettings;
import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.notification.AppointmentReminderLog;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.AppointmentReminderLogRepository;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.service.notification.AppointmentNotificationScheduler;
import br.com.clube_quinze.api.service.settings.SettingsService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Linha do tempo de notificações de cada agendamento (valores padrão):
 * 1h30 antes → pedido de confirmação ao cliente; 1h antes → lembrete simples (dispensado se já confirmou)
 * e aviso ao admin se o cliente não confirmou.
 */
@DisplayName("Scheduler — pedido de confirmação e aviso ao admin")
@SuppressWarnings({"rawtypes", "unchecked"})
class AppointmentConfirmationSchedulerIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AppointmentNotificationScheduler scheduler;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentReminderLogRepository reminderLogRepository;
    @Autowired
    private SettingsService settingsService;

    private final List<Appointment> created = new ArrayList<>();

    @BeforeEach
    void resetMock() {
        Mockito.reset(rabbitTemplate);
    }

    @AfterEach
    void cleanup() {
        appointmentRepository.deleteAll(created);
        created.clear();
        reminderLogRepository.deleteAll();
    }

    private User newClient(String name) {
        String email = "sched_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        ResponseEntity<Map> res = postPublic("/api/v1/auth/register",
                Map.of("name", name, "email", email, "password", "Senha@1234", "membershipTier", "QUINZE_STANDARD"),
                Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return userRepository.findByEmail(email).orElseThrow();
    }

    /** Cria o agendamento direto no banco (a API não permite horários arbitrários como "daqui a 55 min"). */
    private Appointment appointment(User client, int minutesFromNow, AppointmentStatus status, boolean createdEarlier) {
        Appointment a = new Appointment();
        a.setClient(client);
        a.setScheduledAt(LocalDateTime.now(AppConfig.APP_ZONE).plusMinutes(minutesFromNow).truncatedTo(ChronoUnit.SECONDS));
        a.setAppointmentTier(MembershipTier.QUINZE_STANDARD);
        a.setStatus(status);
        a.setDurationMinutes(30);
        a = appointmentRepository.save(a);
        if (createdEarlier) {
            a.setCreatedAt(LocalDateTime.now().minusDays(1));
            a = appointmentRepository.save(a);
        }
        created.add(a);
        return a;
    }

    /** Mensagens enfileiradas até agora. */
    private List<NotificationMessageDTO> sent() {
        List<NotificationMessageDTO> messages = new ArrayList<>();
        Mockito.mockingDetails(rabbitTemplate).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("convertAndSend") && i.getArguments().length == 3)
                .forEach(i -> messages.add((NotificationMessageDTO) i.getArguments()[2]));
        return messages;
    }

    private List<NotificationMessageDTO> pushesTo(Long userId, String type) {
        return sent().stream()
                .filter(m -> m.getType().equals("PUSH_MESSAGE") || m.getType().equals("APPOINTMENT_REMINDER_PUSH"))
                .filter(m -> userId.equals(((Number) m.getData().get("userId")).longValue()))
                .filter(m -> type.equals(m.getData().get("type")) || type.equals(m.getType()))
                .toList();
    }

    private List<NotificationMessageDTO> confirmationEmailsTo(User client) {
        return sent().stream()
                .filter(m -> m.getType().equals("APPOINTMENT_CONFIRMATION_EMAIL"))
                .filter(m -> client.getEmail().equals(m.getData().get("email")))
                .toList();
    }

    private List<NotificationMessageDTO> adminAlertsAbout(String clientName) {
        return sent().stream()
                .filter(m -> "ADMIN_APPOINTMENT_UNCONFIRMED".equals(m.getData().get("type")))
                .filter(m -> String.valueOf(m.getData().get("body")).contains(clientName))
                .toList();
    }

    @Test
    @DisplayName("1h30 antes: cliente recebe o pedido de confirmação (push + e-mail com links assinados), uma única vez")
    void pedeConfirmacaoUmaVez() {
        User client = newClient("Cliente Pedido");
        appointment(client, 85, AppointmentStatus.SCHEDULED, true);

        scheduler.scanAndSendReminders();
        scheduler.scanAndSendReminders();

        List<NotificationMessageDTO> pushes = pushesTo(client.getId(), "APPOINTMENT_CONFIRMATION");
        assertThat(pushes).hasSize(1);
        Map<String, Object> extra = (Map<String, Object>) pushes.get(0).getData().get("data");
        assertThat(extra.get("kind")).isEqualTo("appointment_confirmation");
        assertThat(extra.get("actions")).isEqualTo("confirm,cancel,reschedule");

        List<NotificationMessageDTO> emails = confirmationEmailsTo(client);
        assertThat(emails).hasSize(1);
        Map<String, Object> email = emails.get(0).getData();
        assertThat((String) email.get("confirmUrl")).contains("/appointment-action?token=");
        assertThat((String) email.get("cancelUrl")).contains("/appointment-action?token=");
        assertThat((String) email.get("rescheduleUrl")).contains("/appointment-action?token=");
        assertThat(email.get("confirmUrl")).isNotEqualTo(email.get("cancelUrl"));
    }

    @Test
    @DisplayName("Marcado em cima da hora (depois do momento do pedido) não pede confirmação")
    void naoPedeConfirmacaoSeMarcadoEmCimaDaHora() {
        User client = newClient("Cliente Em Cima Da Hora");
        appointment(client, 85, AppointmentStatus.SCHEDULED, false);

        scheduler.scanAndSendReminders();

        assertThat(pushesTo(client.getId(), "APPOINTMENT_CONFIRMATION")).isEmpty();
        assertThat(confirmationEmailsTo(client)).isEmpty();
    }

    @Test
    @DisplayName("Agendamento já confirmado não recebe o pedido de confirmação")
    void naoPedeConfirmacaoSeJaConfirmado() {
        User client = newClient("Cliente Ja Confirmado");
        appointment(client, 85, AppointmentStatus.CONFIRMED, true);

        scheduler.scanAndSendReminders();

        assertThat(pushesTo(client.getId(), "APPOINTMENT_CONFIRMATION")).isEmpty();
    }

    @Test
    @DisplayName("Lembrete de 1h é dispensado para quem já confirmou, mas enviado para quem não confirmou")
    void lembreteDeUmaHoraDispensadoQuandoConfirmado() {
        User pending = newClient("Cliente Pendente");
        User confirmed = newClient("Cliente Confirmado");
        appointment(pending, 55, AppointmentStatus.SCHEDULED, true);
        appointment(confirmed, 55, AppointmentStatus.CONFIRMED, true);

        scheduler.scanAndSendReminders();

        assertThat(pushesTo(pending.getId(), "APPOINTMENT_REMINDER_PUSH")).hasSize(1);
        assertThat(pushesTo(confirmed.getId(), "APPOINTMENT_REMINDER_PUSH")).isEmpty();
    }

    @Test
    @DisplayName("1h antes: admin é avisado se o cliente recebeu o pedido e não confirmou (uma vez só)")
    void avisaAdminQuandoClienteNaoConfirmou() {
        User silent = newClient("Cliente Silencioso");
        User confirmed = newClient("Cliente Que Confirmou");
        User neverAsked = newClient("Cliente Nunca Perguntado");
        Appointment a1 = appointment(silent, 55, AppointmentStatus.SCHEDULED, true);
        Appointment a2 = appointment(confirmed, 55, AppointmentStatus.CONFIRMED, true);
        Appointment a3 = appointment(neverAsked, 55, AppointmentStatus.SCHEDULED, true);
        // a1 e a2 receberam o pedido de confirmação às 1h30; a3 não (ex.: foi remarcado depois).
        reminderLogRepository.save(new AppointmentReminderLog(a1.getId(), 90, "CLIENT_CONFIRM"));
        reminderLogRepository.save(new AppointmentReminderLog(a2.getId(), 90, "CLIENT_CONFIRM"));

        scheduler.scanAndSendReminders();
        scheduler.scanAndSendReminders();

        List<User> admins = userRepository.findByRole(RoleType.CLUB_ADMIN);
        assertThat(adminAlertsAbout("Cliente Silencioso")).hasSize(admins.size());
        NotificationMessageDTO alert = adminAlertsAbout("Cliente Silencioso").get(0);
        Map<String, Object> extra = (Map<String, Object>) alert.getData().get("data");
        assertThat(extra.get("kind")).isEqualTo("admin_unconfirmed");
        assertThat(extra.get("actions")).isEqualTo("keep,cancel,reschedule");
        assertThat(extra.get("appointmentId")).isEqualTo(a1.getId());
        assertThat(adminAlertsAbout("Cliente Que Confirmou")).isEmpty();
        assertThat(adminAlertsAbout("Cliente Nunca Perguntado")).isEmpty();
    }

    @Test
    @DisplayName("Com o fluxo desligado nas configurações, nada de pedido de confirmação nem aviso ao admin")
    void fluxoDesligadoNaoEnviaNada() {
        AppointmentConfirmationSettings original = settingsService.getAppointmentConfirmationSettings();
        try {
            settingsService.updateAppointmentConfirmationSettings(new AppointmentConfirmationSettings(
                    false, original.confirmationRequestOffset(), original.adminAlertOffset(),
                    original.clientReminderOffsets(), original.skipRemindersWhenConfirmed()));

            User client = newClient("Cliente Fluxo Desligado");
            Appointment a = appointment(client, 85, AppointmentStatus.SCHEDULED, true);
            Appointment b = appointment(newClient("Cliente Desligado Admin"), 55, AppointmentStatus.SCHEDULED, true);
            reminderLogRepository.save(new AppointmentReminderLog(b.getId(), 90, "CLIENT_CONFIRM"));

            scheduler.scanAndSendReminders();

            assertThat(pushesTo(client.getId(), "APPOINTMENT_CONFIRMATION")).isEmpty();
            assertThat(adminAlertsAbout("Cliente Desligado Admin")).isEmpty();
        } finally {
            settingsService.updateAppointmentConfirmationSettings(original);
        }
    }

    @Test
    @DisplayName("Prazos vêm das configurações: pedido de confirmação com 2h de antecedência")
    void respeitaPrazosConfigurados() {
        AppointmentConfirmationSettings original = settingsService.getAppointmentConfirmationSettings();
        try {
            settingsService.updateAppointmentConfirmationSettings(new AppointmentConfirmationSettings(
                    true, 120, 60, original.clientReminderOffsets(), original.skipRemindersWhenConfirmed()));

            User client = newClient("Cliente Prazo Custom");
            appointment(client, 115, AppointmentStatus.SCHEDULED, true);

            scheduler.scanAndSendReminders();

            assertThat(pushesTo(client.getId(), "APPOINTMENT_CONFIRMATION")).hasSize(1);
        } finally {
            settingsService.updateAppointmentConfirmationSettings(original);
        }
    }
}
