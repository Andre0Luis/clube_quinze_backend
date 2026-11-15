package br.com.clube_quinze.api.integration.google;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.google.GoogleCalendarConnection;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.GoogleCalendarConnectionRepository;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleEvent;
import br.com.clube_quinze.api.service.google.GoogleCalendarService;
import br.com.clube_quinze.api.service.google.TokenCipher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.google.client-id=cliente-de-teste",
        "app.google.client-secret=segredo-de-teste",
        // 32 bytes em base64: "0123456789abcdef0123456789abcdef"
        "app.google.token-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.google.app-return-url=clubequinze://admin-google-calendar"})
@Import(GoogleCalendarIntegrationTest.FakeGoogleConfig.class)
@DisplayName("Google Agenda — conexão, escolha da agenda e sincronização")
@SuppressWarnings({"rawtypes", "unchecked"})
class GoogleCalendarIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class FakeGoogleConfig {
        @Bean
        @Primary
        FakeGoogleCalendarGateway fakeGoogleCalendarGateway() {
            return new FakeGoogleCalendarGateway();
        }
    }

    private static final String BASE = "/api/v1/admin/integrations/google";
    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired private FakeGoogleCalendarGateway fake;
    @Autowired private GoogleCalendarGateway gateway; // é o mesmo falso
    @Autowired private GoogleCalendarService service;
    @Autowired private GoogleCalendarConnectionRepository connections;
    @Autowired private AppointmentRepository appointments;
    @Autowired private TokenCipher cipher;

    private String adminToken;
    private String memberToken;
    private Long memberId;

    @BeforeEach
    void setup() {
        service.disconnect();
        fake.reset();
        adminToken = adminToken();
        String email = "google_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        ResponseEntity<Map> reg = postPublic("/api/v1/auth/register", Map.of("name", "Cliente Agenda", "email", email,
                "password", "Senha@1234", "membershipTier", "QUINZE_STANDARD"), Map.class);
        memberToken = (String) reg.getBody().get("accessToken");
        memberId = ((Number) get("/api/v1/users/me", memberToken, Map.class).getBody().get("id")).longValue();
    }

    /** Conecta a conta pelo fluxo real (connect + callback) e devolve a resposta do callback. */
    private ResponseEntity<String> connect() {
        ResponseEntity<Map> start = post(BASE + "/connect", Map.of(), adminToken, Map.class);
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        String authUrl = (String) start.getBody().get("authUrl");
        String state = authUrl.substring(authUrl.indexOf("state=") + 6);
        return restTemplate.getForEntity(url("/integrations/google/callback?code=codigo-1&state=" + state), String.class);
    }

    private Long newAppointment() {
        LocalDate day = LocalDate.now().plusDays(950 + DAY.getAndIncrement());
        ResponseEntity<Map> created = post("/api/v1/appointments", Map.of(
                "clientId", memberId,
                "scheduledAt", day.atTime(10, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD", "serviceType", "Corte Agenda"), memberToken, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) created.getBody().get("id")).longValue();
    }

    private GoogleEvent eventOf(Long appointmentId) {
        Appointment a = appointments.findById(appointmentId).orElseThrow();
        return a.getGoogleEventId() == null ? null : fake.events.get(a.getGoogleEventId());
    }

    private void connectAndChooseNewCalendar() {
        assertThat(connect().getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> chosen = put(BASE + "/calendar", Map.of("mode", "CREATE", "name", "Agenda do Clube"), adminToken, Map.class);
        assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Só administrador usa a integração; cliente recebe 403")
    void soAdministrador() {
        assertThat(get(BASE, memberToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(BASE + "/connect", Map.of(), memberToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(BASE + "/calendars", memberToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Conectar: o callback guarda a conta, o refresh token vai criptografado e o estado pede a escolha da agenda")
    void conectaAContaECriptografaOToken() {
        assertThat(get(BASE, adminToken, Map.class).getBody().get("status")).isEqualTo("DISCONNECTED");

        ResponseEntity<String> callback = connect();

        assertThat(callback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(callback.getBody()).contains("Google Agenda conectado", FakeGoogleCalendarGateway.ACCOUNT_EMAIL,
                "clubequinze://admin-google-calendar?status=connected");
        Map status = get(BASE, adminToken, Map.class).getBody();
        assertThat(status.get("status")).isEqualTo("NEEDS_CALENDAR");
        assertThat(status.get("googleEmail")).isEqualTo(FakeGoogleCalendarGateway.ACCOUNT_EMAIL);

        GoogleCalendarConnection stored = connections.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(stored.getRefreshTokenEnc()).doesNotContain(FakeGoogleCalendarGateway.REFRESH_TOKEN);
        assertThat(cipher.decrypt(stored.getRefreshTokenEnc())).isEqualTo(FakeGoogleCalendarGateway.REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Callback com state inválido, ausente ou sem refresh token não conecta nada")
    void callbackInvalidoNaoConecta() {
        ResponseEntity<String> forged = restTemplate.getForEntity(url("/integrations/google/callback?code=x&state=forjado"), String.class);
        assertThat(forged.getBody()).contains("Não foi possível conectar", "status=error");

        ResponseEntity<String> denied = restTemplate.getForEntity(url("/integrations/google/callback?error=access_denied"), String.class);
        assertThat(denied.getBody()).contains("cancelada", "status=cancelled");

        ResponseEntity<Map> start = post(BASE + "/connect", Map.of(), adminToken, Map.class);
        String authUrl = (String) start.getBody().get("authUrl");
        String state = authUrl.substring(authUrl.indexOf("state=") + 6);
        ResponseEntity<String> noRefresh = restTemplate.getForEntity(url("/integrations/google/callback?code=sem-refresh&state=" + state), String.class);
        assertThat(noRefresh.getBody()).contains("acesso contínuo");

        assertThat(connections.findFirstByOrderByIdAsc()).isEmpty();
    }

    @Test
    @DisplayName("Escolher a agenda: criar uma nova ou usar uma existente; agenda inexistente é recusada")
    void escolheAAgenda() {
        assertThat(connect().getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> calendars = get(BASE + "/calendars", adminToken, List.class);
        assertThat(calendars.getBody()).hasSize(2);

        assertThat(put(BASE + "/calendar", Map.of("mode", "EXISTING", "calendarId", "nao-existe"), adminToken, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> existing = put(BASE + "/calendar",
                Map.of("mode", "EXISTING", "calendarId", "equipe@group.calendar.google.com"), adminToken, Map.class);
        assertThat(existing.getBody().get("status")).isEqualTo("READY");
        assertThat(existing.getBody().get("calendarSummary")).isEqualTo("Equipe");

        ResponseEntity<Map> created = put(BASE + "/calendar", Map.of("mode", "CREATE", "name", "Agenda do Clube"), adminToken, Map.class);
        assertThat(created.getBody().get("calendarSummary")).isEqualTo("Agenda do Clube");
        assertThat(fake.createdCalendars).containsExactly("Agenda do Clube|America/Sao_Paulo");
    }

    @Test
    @DisplayName("Sincroniza o ciclo de vida: criado (amarelo), confirmado (verde), remarcação pendente (laranja) e cancelado (removido)")
    void sincronizaOCicloDeVida() {
        Long id = newAppointment();
        connectAndChooseNewCalendar();

        service.syncAppointment(id);
        GoogleEvent created = eventOf(id);
        assertThat(created).isNotNull();
        assertThat(created.summary()).isEqualTo("Corte Agenda — Cliente Agenda");
        assertThat(created.colorId()).isEqualTo("5");
        assertThat(created.timeZone()).isEqualTo("America/Sao_Paulo");
        assertThat(created.description()).contains("Agendado (aguardando confirmação)", "Cliente: Cliente Agenda");
        String firstEventId = appointments.findById(id).orElseThrow().getGoogleEventId();

        post("/api/v1/appointments/" + id + "/confirm", Map.of(), memberToken, Map.class);
        service.syncAppointment(id);
        assertThat(eventOf(id).colorId()).isEqualTo("10");
        assertThat(appointments.findById(id).orElseThrow().getGoogleEventId()).isEqualTo(firstEventId); // atualizou o mesmo evento

        LocalDate other = LocalDate.now().plusDays(950 + DAY.getAndIncrement());
        post("/api/v1/appointments/" + id + "/reschedule-request",
                Map.of("newDate", other.atTime(16, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), memberToken, Map.class);
        service.syncAppointment(id);
        assertThat(eventOf(id).colorId()).isEqualTo("6");
        assertThat(eventOf(id).summary()).startsWith("Remarcação pendente — ");
        assertThat(eventOf(id).description()).contains("Novo horário proposto");

        post("/api/v1/appointments/" + id + "/reschedule-approve", Map.of("durationMinutes", 45), adminToken, Map.class);
        service.syncAppointment(id);
        GoogleEvent approved = eventOf(id);
        assertThat(approved.colorId()).isEqualTo("10");
        assertThat(approved.start()).isEqualTo(LocalDateTime.of(other, java.time.LocalTime.of(16, 0)));
        assertThat(approved.end()).isEqualTo(LocalDateTime.of(other, java.time.LocalTime.of(16, 45)));

        delete("/api/v1/appointments/" + id, memberToken);
        service.syncAppointment(id);
        assertThat(fake.events).doesNotContainKey(firstEventId);
        assertThat(appointments.findById(id).orElseThrow().getGoogleEventId()).isNull();
    }

    @Test
    @DisplayName("Reconciliador sincroniza o que ficou para trás e não entra em laço (updatedAt não muda ao gravar o vínculo)")
    void reconciliadorNaoEntraEmLaco() {
        Long id = newAppointment();
        connectAndChooseNewCalendar();

        assertThat(eventOf(id)).isNull();
        service.reconcile(500);
        assertThat(eventOf(id)).isNotNull();

        LocalDateTime updatedBefore = appointments.findById(id).orElseThrow().getUpdatedAt();
        assertThat(appointments.findNeedingGoogleSync(LocalDateTime.now().minusDays(1),
                org.springframework.data.domain.PageRequest.of(0, 500))).extracting(Appointment::getId).doesNotContain(id);
        assertThat(appointments.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(updatedBefore);
    }

    @Test
    @DisplayName("Sem agenda escolhida nada é sincronizado")
    void semAgendaNaoSincroniza() {
        Long id = newAppointment();
        assertThat(connect().getStatusCode()).isEqualTo(HttpStatus.OK);

        service.syncAppointment(id);

        assertThat(fake.events).isEmpty();
        assertThat(appointments.findById(id).orElseThrow().getGoogleEventId()).isNull();
    }

    @Test
    @DisplayName("Acesso revogado no Google marca REVOKED (mesmo com a sincronização falhando) e exige reconectar")
    void acessoRevogado() {
        connectAndChooseNewCalendar();
        Long id = newAppointment();
        fake.refreshFails = true;
        service.invalidateAccessTokenCache();

        org.junit.jupiter.api.Assertions.assertThrows(GoogleCalendarGateway.GoogleAuthException.class,
                () -> service.syncAppointment(id));

        assertThat(get(BASE, adminToken, Map.class).getBody().get("status")).isEqualTo("REVOKED");
        ResponseEntity<Map> calendars = get(BASE + "/calendars", adminToken, Map.class);
        assertThat(calendars.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(calendars.getBody().get("message").toString()).contains("revogado");
        assertThat(service.reconcile(500)).isZero(); // sem agenda pronta, nada é tentado
    }

    @Test
    @DisplayName("Desconectar revoga o token no Google, apaga as credenciais e zera o vínculo dos agendamentos")
    void desconecta() {
        Long id = newAppointment();
        connectAndChooseNewCalendar();
        service.syncAppointment(id);
        assertThat(appointments.findById(id).orElseThrow().getGoogleEventId()).isNotNull();

        ResponseEntity<Void> response = delete(BASE, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fake.revoked).containsExactly(FakeGoogleCalendarGateway.REFRESH_TOKEN);
        assertThat(connections.findFirstByOrderByIdAsc()).isEmpty();
        assertThat(get(BASE, adminToken, Map.class).getBody().get("status")).isEqualTo("DISCONNECTED");
        assertThat(appointments.findById(id).orElseThrow().getGoogleEventId()).isNull();
    }
}
