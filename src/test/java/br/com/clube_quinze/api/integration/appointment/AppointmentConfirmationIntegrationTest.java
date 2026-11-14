package br.com.clube_quinze.api.integration.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import java.time.Instant;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Links assinados do e-mail e parâmetros do fluxo de confirmação (painel de personalização). */
@DisplayName("Confirmação de agendamentos — links do e-mail e configurações")
@SuppressWarnings({"rawtypes", "unchecked"})
class AppointmentConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";
    private static final String SETTINGS = "/api/v1/admin/settings/appointments";
    // Dias exclusivos desta classe (os outros testes usam 14–73 e 120+).
    private static final AtomicInteger DAY_COUNTER = new AtomicInteger(0);

    @Autowired
    private AppointmentActionTokenService tokenService;

    private String adminToken;
    private String memberToken;
    private Long memberId;

    @BeforeEach
    void setup() {
        adminToken = adminToken();
        String email = "confirm_member_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> reg = Map.of("name", "Membro Confirmação", "email", email,
                "password", "Senha@1234", "membershipTier", "QUINZE_STANDARD");
        ResponseEntity<Map> res = postPublic("/api/v1/auth/register", reg, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        memberToken = (String) res.getBody().get("accessToken");
        memberId = ((Number) get("/api/v1/users/me", memberToken, Map.class).getBody().get("id")).longValue();
    }

    private Long createAppointment() {
        LocalDateTime at = LocalDate.now().plusDays(200L + DAY_COUNTER.getAndIncrement()).atTime(10, 0);
        Map<String, Object> body = Map.of(
                "clientId", memberId,
                "scheduledAt", at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD",
                "serviceType", "Corte link");
        ResponseEntity<Map> created = post(BASE, body, memberToken, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) created.getBody().get("id")).longValue();
    }

    private String statusOf(Long id) {
        return (String) get(BASE + "/" + id, adminToken, Map.class).getBody().get("status");
    }

    private String token(Long id, Action action) {
        return tokenService.create(id, action, Instant.now().plusSeconds(3600));
    }

    private ResponseEntity<String> postAction(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        return restTemplate.postForEntity(url("/appointment-action"), new HttpEntity<>(form, headers), String.class);
    }

    @Test
    @DisplayName("GET do link só mostra a ação: abrir o link (ex.: scanner de e-mail) não confirma nem cancela")
    void getNaoExecutaAcao() {
        Long id = createAppointment();

        ResponseEntity<String> confirmPage = restTemplate.getForEntity(
                url("/appointment-action?token=" + token(id, Action.CONFIRM)), String.class);
        ResponseEntity<String> cancelPage = restTemplate.getForEntity(
                url("/appointment-action?token=" + token(id, Action.CANCEL)), String.class);

        assertThat(confirmPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmPage.getBody()).contains("Confirmar presença");
        assertThat(cancelPage.getBody()).contains("Cancelar agendamento");
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("POST com token de confirmar → CONFIRMED (sem JWT); repetir é idempotente")
    void postConfirma() {
        Long id = createAppointment();

        ResponseEntity<String> first = postAction(token(id, Action.CONFIRM));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("Presença confirmada");
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");

        assertThat(postAction(token(id, Action.CONFIRM)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("POST com token de cancelar → CANCELED; depois o link de confirmar avisa que já foi cancelado")
    void postCancela() {
        Long id = createAppointment();

        ResponseEntity<String> cancel = postAction(token(id, Action.CANCEL));
        assertThat(cancel.getBody()).contains("Agendamento cancelado");
        assertThat(statusOf(id)).isEqualTo("CANCELED");

        ResponseEntity<String> confirmAfter = restTemplate.getForEntity(
                url("/appointment-action?token=" + token(id, Action.CONFIRM)), String.class);
        assertThat(confirmAfter.getBody()).contains("já está cancelado");
    }

    @Test
    @DisplayName("Token de remarcar leva ao app e não executa nada")
    void tokenDeRemarcarApontaParaOApp() {
        Long id = createAppointment();

        ResponseEntity<String> page = restTemplate.getForEntity(
                url("/appointment-action?token=" + token(id, Action.RESCHEDULE)), String.class);
        assertThat(page.getBody()).contains("Abrir o aplicativo");

        ResponseEntity<String> post = postAction(token(id, Action.RESCHEDULE));
        assertThat(post.getBody()).contains("abra o aplicativo");
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("Token inválido, adulterado ou expirado não executa nada")
    void tokenInvalidoNaoExecuta() {
        Long id = createAppointment();
        String expired = tokenService.create(id, Action.CANCEL, Instant.now().minusSeconds(10));
        String valid = token(id, Action.CANCEL);
        String tampered = valid.substring(0, valid.length() - 2) + "xx";

        for (String bad : List.of(expired, tampered, "lixo")) {
            ResponseEntity<String> post = postAction(bad);
            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody()).contains("inválido ou expirado");
        }
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("GET/PUT /admin/settings/appointments → admin lê padrões, valida e atualiza; cliente recebe 403")
    void configuracoesDoFluxo() {
        ResponseEntity<Map> defaults = get(SETTINGS, adminToken, Map.class);
        assertThat(defaults.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(defaults.getBody().get("enabled")).isEqualTo(true);
        assertThat(defaults.getBody().get("confirmationRequestOffset")).isEqualTo(90);
        assertThat(defaults.getBody().get("adminAlertOffset")).isEqualTo(60);
        assertThat((List<Integer>) defaults.getBody().get("clientReminderOffsets")).containsExactly(1440, 180, 60, 30);
        assertThat((List<Integer>) defaults.getBody().get("skipRemindersWhenConfirmed")).containsExactly(60);

        assertThat(get(SETTINGS, memberToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // O aviso ao admin precisa vir depois do pedido de confirmação.
        Map<String, Object> invalid = Map.of("enabled", true, "confirmationRequestOffset", 60, "adminAlertOffset", 90,
                "clientReminderOffsets", List.of(60), "skipRemindersWhenConfirmed", List.of());
        assertThat(put(SETTINGS, invalid, adminToken, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        try {
            Map<String, Object> custom = Map.of("enabled", false, "confirmationRequestOffset", 120, "adminAlertOffset", 45,
                    "clientReminderOffsets", List.of(30, 1440, 30), "skipRemindersWhenConfirmed", List.of());
            ResponseEntity<Map> updated = put(SETTINGS, custom, adminToken, Map.class);
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().get("enabled")).isEqualTo(false);
            assertThat(updated.getBody().get("confirmationRequestOffset")).isEqualTo(120);
            assertThat((List<Integer>) updated.getBody().get("clientReminderOffsets")).containsExactly(1440, 30);
            assertThat((List<Integer>) updated.getBody().get("skipRemindersWhenConfirmed")).isEmpty();
        } finally {
            Map<String, Object> restore = Map.of("enabled", true, "confirmationRequestOffset", 90, "adminAlertOffset", 60,
                    "clientReminderOffsets", List.of(1440, 180, 60, 30), "skipRemindersWhenConfirmed", List.of(60));
            put(SETTINGS, restore, adminToken, Map.class);
        }
    }
}
