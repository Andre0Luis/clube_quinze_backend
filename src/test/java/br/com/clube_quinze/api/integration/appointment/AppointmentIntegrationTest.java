package br.com.clube_quinze.api.integration.appointment;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para AppointmentController.
 *
 * Cenários cobertos:
 *  - Consulta de slots disponíveis
 *  - Agendamento de horário pelo membro
 *  - Busca de agendamento por ID
 *  - Listagem dos próprios agendamentos
 *  - Atualização de status (admin/employee)
 *  - Listagem geral (admin/employee)
 *  - Reagendamento
 *  - Cancelamento
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Agendamentos — Testes de Integração")
class AppointmentIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";
    private static Long createdAppointmentId;
    private String memberToken;
    private Long memberId;
    private String adminToken;

    @BeforeEach
    void setup() {
        adminToken = adminToken();

        // Cria membro
        String memberEmail = "appt_member_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Membro Agendamento",
                "email", memberEmail,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic("/api/v1/auth/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        memberToken = (String) regRes.getBody().get("accessToken");

        // Obtem ID do membro
        ResponseEntity<Map> meRes = get("/api/v1/users/me", memberToken, Map.class);
        memberId = ((Number) meRes.getBody().get("id")).longValue();
    }

    @Test
    @Order(1)
    @DisplayName("GET /appointments/availability → 200 lista slots disponíveis")
    void deveListarSlotsDisponiveis() {
        String date = LocalDate.now().plusDays(3).format(DateTimeFormatter.ISO_DATE);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                url(BASE + "/availability?date=" + date), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("POST /appointments → 201 membro agenda horário")
    void deveMembrosAgendarHorario() {
        // Data futura garantida: 7 dias à frente, meio-dia
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(7).withHour(12).withMinute(0).withSecond(0).withNano(0);

        Map<String, Object> request = Map.of(
                "clientId", memberId,
                "scheduledAt", scheduledAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD",
                "serviceType", "Corte Integração",
                "notes", "Teste de integração",
                "durationMinutes", 60
        );

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");

        createdAppointmentId = ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    @Order(3)
    @DisplayName("GET /appointments/{id} → 200 membro busca seu agendamento")
    void deveBuscarAgendamentoPorId() {
        if (createdAppointmentId == null) deveMembrosAgendarHorario();

        ResponseEntity<Map> response = get(BASE + "/" + createdAppointmentId, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(createdAppointmentId.intValue());
    }

    @Test
    @Order(4)
    @DisplayName("GET /appointments/me → 200 lista agendamentos do membro")
    void deveListarAgendamentosDoMembro() {
        if (createdAppointmentId == null) deveMembrosAgendarHorario();

        ResponseEntity<Map> response = get(BASE + "/me", memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @Order(5)
    @DisplayName("GET /appointments → 200 admin lista todos os agendamentos")
    void deveAdminListarTodosAgendamentos() {
        ResponseEntity<Map> response = get(BASE, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @Order(6)
    @DisplayName("GET /appointments → 403 membro não pode listar todos agendamentos")
    void deveRetornar403QuandoMembroListaTodosAgendamentos() {
        ResponseEntity<Map> response = get(BASE, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(7)
    @DisplayName("PATCH /appointments/{id}/status → 200 admin atualiza status")
    void deveAdminAtualizarStatus() {
        if (createdAppointmentId == null) deveMembrosAgendarHorario();

        Map<String, String> statusReq = Map.of("status", "CONFIRMED");

        ResponseEntity<Map> response = patch(
                BASE + "/" + createdAppointmentId + "/status",
                statusReq, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CONFIRMED");
    }

    @Test
    @Order(8)
    @DisplayName("PUT /appointments/{id}/reschedule → 200 membro reagenda")
    void deveMembrosReagendarHorario() {
        if (createdAppointmentId == null) deveMembrosAgendarHorario();

        LocalDateTime newTime = LocalDateTime.now().plusDays(14).withHour(10).withMinute(0).withSecond(0).withNano(0);

        Map<String, String> rescheduleReq = Map.of(
                "scheduledAt", newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        ResponseEntity<Map> response = put(
                BASE + "/" + createdAppointmentId + "/reschedule",
                rescheduleReq, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(9)
    @DisplayName("DELETE /appointments/{id} → 204 membro cancela agendamento")
    void deveMembroCancelarAgendamento() {
        // Cria um agendamento específico para cancelar
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(21).withHour(14).withMinute(0).withSecond(0).withNano(0);

        Map<String, Object> request = Map.of(
                "clientId", memberId,
                "scheduledAt", scheduledAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD",
                "serviceType", "Corte Cancelamento",
                "durationMinutes", 60
        );

        ResponseEntity<Map> createRes = post(BASE, request, memberToken, Map.class);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long appointmentToCancel = ((Number) createRes.getBody().get("id")).longValue();

        ResponseEntity<Void> response = delete(BASE + "/" + appointmentToCancel, memberToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
