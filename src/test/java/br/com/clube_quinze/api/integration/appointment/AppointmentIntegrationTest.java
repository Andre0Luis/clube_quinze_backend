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
import java.util.concurrent.atomic.AtomicInteger;

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
@SuppressWarnings({"rawtypes", "unchecked"})
class AppointmentIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";
    private static final AtomicInteger SLOT_COUNTER = new AtomicInteger(0);
    private Long createdAppointmentId;
    private String memberToken;
    private Long memberId;
    private String adminToken;

    private LocalDateTime uniqueFutureSlot() {
        int slotIndex = SLOT_COUNTER.getAndIncrement();
        int dayOffset = 7 + slotIndex;
        int hourOffset = slotIndex % 6;
        return LocalDateTime.now()
                .plusDays(dayOffset)
                .withHour(10 + hourOffset)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

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

        ResponseEntity<Map> response = get(BASE + "/availability?date=" + date, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("POST /appointments → 201 membro agenda horário")
    void deveMembrosAgendarHorario() {
        // Data futura garantida: 7 dias à frente, meio-dia
        LocalDateTime scheduledAt = uniqueFutureSlot();

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
        assertThat(response.getBody().get("status")).isEqualTo("SCHEDULED");

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

        Map<String, String> statusReq = Map.of("status", "COMPLETED");

        ResponseEntity<Map> response = patch(
                BASE + "/" + createdAppointmentId + "/status",
                statusReq, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @Order(8)
    @DisplayName("PUT /appointments/{id}/reschedule → 200 membro reagenda")
    void deveMembrosReagendarHorario() {
        if (createdAppointmentId == null) deveMembrosAgendarHorario();

        LocalDateTime newTime = uniqueFutureSlot().plusDays(10);

        Map<String, String> rescheduleReq = Map.of(
            "newDate", newTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
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
        LocalDateTime scheduledAt = uniqueFutureSlot().plusDays(15);

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

    // ── Duração flexível (admin) ──────────────────────────────────────────────

    private static final AtomicInteger DURATION_DAY_COUNTER = new AtomicInteger(0);

    /** Dia exclusivo por teste, fora da faixa (14–73 dias) que os testes de feedback sorteiam. */
    private LocalDate freshDay() {
        return LocalDate.now().plusDays(120L + DURATION_DAY_COUNTER.getAndIncrement());
    }

    private Map<String, Object> adminAppointment(LocalDateTime at, Integer duration) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("clientId", memberId);
        body.put("scheduledAt", at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        body.put("appointmentTier", "QUINZE_STANDARD");
        body.put("serviceType", "Corte duração flexível");
        if (duration != null) {
            body.put("durationMinutes", duration);
        }
        return body;
    }

    @Test
    @Order(10)
    @DisplayName("POST /appointments → admin define duração de 45 min e horários colados são aceitos")
    void adminDeveDefinirDuracaoPersonalizada() {
        LocalDate day = freshDay();

        ResponseEntity<Map> first = post(BASE, adminAppointment(day.atTime(14, 15), 45), adminToken, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) first.getBody().get("durationMinutes")).intValue()).isEqualTo(45);

        // 14:15 + 45 min termina 15:00 → um atendimento às 15:00 não conflita
        ResponseEntity<Map> backToBack = post(BASE, adminAppointment(day.atTime(15, 0), 30), adminToken, Map.class);
        assertThat(backToBack.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(11)
    @DisplayName("POST /appointments → 422 quando a duração personalizada sobrepõe outro atendimento")
    void deveRecusarSobreposicaoComDuracaoPersonalizada() {
        LocalDate day = freshDay();
        assertThat(post(BASE, adminAppointment(day.atTime(14, 15), 45), adminToken, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> overlap = post(BASE, adminAppointment(day.atTime(14, 45), 30), adminToken, Map.class);

        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @Order(12)
    @DisplayName("POST /appointments → duração inválida (não múltipla de 15 / fora do limite) é recusada")
    void deveValidarDuracao() {
        LocalDate day = freshDay();

        ResponseEntity<Map> notMultiple = post(BASE, adminAppointment(day.atTime(10, 0), 20), adminToken, Map.class);
        assertThat(notMultiple.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> tooShort = post(BASE, adminAppointment(day.atTime(10, 0), 10), adminToken, Map.class);
        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> tooLong = post(BASE, adminAppointment(day.atTime(10, 0), 600), adminToken, Map.class);
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> offGrid = post(BASE, adminAppointment(day.atTime(10, 10), 30), adminToken, Map.class);
        assertThat(offGrid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @Order(13)
    @DisplayName("POST /appointments → 422 quando o atendimento terminaria depois do fechamento")
    void deveRecusarAtendimentoQueUltrapassaFechamento() {
        LocalDate day = freshDay();

        // Fecha às 21:00: 20:30 + 60 min estoura o expediente; 20:00 + 60 min cabe.
        ResponseEntity<Map> late = post(BASE, adminAppointment(day.atTime(20, 30), 60), adminToken, Map.class);
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> fits = post(BASE, adminAppointment(day.atTime(20, 0), 60), adminToken, Map.class);
        assertThat(fits.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(14)
    @DisplayName("POST /appointments → cliente não escolhe duração: vale a padrão do plano")
    void clienteNaoControlaDuracao() {
        LocalDate day = freshDay();

        ResponseEntity<Map> response = post(BASE, adminAppointment(day.atTime(11, 0), 240), memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) response.getBody().get("durationMinutes")).intValue()).isEqualTo(30);
    }

    @Test
    @Order(15)
    @DisplayName("PUT /appointments/{id}/reschedule → admin altera a duração; cliente não")
    void soAdminAlteraDuracaoNoReagendamento() {
        LocalDate day = freshDay();
        ResponseEntity<Map> created = post(BASE, adminAppointment(day.atTime(10, 0), 30), adminToken, Map.class);
        Long id = ((Number) created.getBody().get("id")).longValue();
        String newDate = day.atTime(16, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ResponseEntity<Map> byMember = put(BASE + "/" + id + "/reschedule",
                Map.of("newDate", newDate, "durationMinutes", 240), memberToken, Map.class);
        assertThat(byMember.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) byMember.getBody().get("durationMinutes")).intValue()).isEqualTo(30);

        ResponseEntity<Map> byAdmin = put(BASE + "/" + id + "/reschedule",
                Map.of("newDate", newDate, "durationMinutes", 90), adminToken, Map.class);
        assertThat(byAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) byAdmin.getBody().get("durationMinutes")).intValue()).isEqualTo(90);
    }

    @Test
    @Order(16)
    @DisplayName("GET /appointments/availability → admin com duração vê grade de 15 min; cliente ignora o parâmetro")
    void disponibilidadeComDuracaoUsaGradeDe15Minutos() {
        String date = freshDay().format(DateTimeFormatter.ISO_DATE);

        ResponseEntity<Map> admin = get(BASE + "/availability?date=" + date + "&durationMinutes=45", adminToken, Map.class);
        ResponseEntity<Map> member = get(BASE + "/availability?date=" + date + "&durationMinutes=45", memberToken, Map.class);

        java.util.List<String> adminSlots = (java.util.List<String>) admin.getBody().get("availableSlots");
        java.util.List<String> memberSlots = (java.util.List<String>) member.getBody().get("availableSlots");
        assertThat(adminSlots).anyMatch(slot -> slot.contains("T09:15"));
        // 45 min a partir de 20:15 termina 21:00 (cabe); 20:30 + 45 não cabe.
        assertThat(adminSlots).anyMatch(slot -> slot.contains("T20:15"));
        assertThat(adminSlots).noneMatch(slot -> slot.contains("T20:30"));
        assertThat(memberSlots).noneMatch(slot -> slot.contains("T09:15"));
    }
}
