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
    @DisplayName("PUT /appointments/{id}/reschedule → 200 membro propõe remarcação (fica pendente de aprovação)")
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
        // Cliente nunca remarca direto: vira proposta e o horário original segue valendo.
        assertThat(response.getBody().get("status")).isEqualTo("RESCHEDULE_REQUESTED");
        assertThat(response.getBody().get("proposedAt")).isNotNull();
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

        // Fecha às 22:00: 21:30 + 60 min estoura o expediente; 21:00 + 60 min cabe.
        ResponseEntity<Map> late = post(BASE, adminAppointment(day.atTime(21, 30), 60), adminToken, Map.class);
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> fits = post(BASE, adminAppointment(day.atTime(21, 0), 60), adminToken, Map.class);
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
        assertThat(byMember.getBody().get("status")).isEqualTo("RESCHEDULE_REQUESTED");

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
        // 45 min a partir de 21:15 termina 22:00 (cabe); 21:30 + 45 não cabe.
        assertThat(adminSlots).anyMatch(slot -> slot.contains("T21:15"));
        assertThat(adminSlots).noneMatch(slot -> slot.contains("T21:30"));
        assertThat(memberSlots).noneMatch(slot -> slot.contains("T09:15"));
    }

    // ── Confirmação e proposta de remarcação ──────────────────────────────────

    /** Cria um agendamento do membro (padrão do plano, 30 min) em um dia exclusivo e devolve o id. */
    private Long memberAppointmentAt(LocalDateTime at) {
        ResponseEntity<Map> created = post(BASE, adminAppointment(at, null), memberToken, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) created.getBody().get("id")).longValue();
    }

    private Map getById(Long id) {
        return get(BASE + "/" + id, adminToken, Map.class).getBody();
    }

    private String iso(LocalDateTime dt) {
        return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Test
    @Order(17)
    @DisplayName("POST /appointments/{id}/confirm → cliente confirma (idempotente) e vira CONFIRMED")
    void clienteConfirmaAgendamento() {
        Long id = memberAppointmentAt(freshDay().atTime(10, 0));

        ResponseEntity<Map> first = post(BASE + "/" + id + "/confirm", Map.of(), memberToken, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status")).isEqualTo("CONFIRMED");
        assertThat(first.getBody().get("confirmedAt")).isNotNull();

        ResponseEntity<Map> again = post(BASE + "/" + id + "/confirm", Map.of(), memberToken, Map.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().get("status")).isEqualTo("CONFIRMED");
    }

    @Test
    @Order(18)
    @DisplayName("POST /appointments/{id}/confirm → outro cliente não pode confirmar")
    void outroClienteNaoConfirma() {
        Long id = memberAppointmentAt(freshDay().atTime(10, 0));

        ResponseEntity<Map> other = post(BASE + "/" + id + "/confirm", Map.of(), loginAsMember(), Map.class);

        assertThat(other.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(getById(id).get("status")).isEqualTo("SCHEDULED");
    }

    @Test
    @Order(19)
    @DisplayName("DELETE /appointments/{id} → cliente cancela; depois não dá mais para confirmar")
    void clienteCancelaEDepoisNaoConfirma() {
        Long id = memberAppointmentAt(freshDay().atTime(10, 0));

        assertThat(delete(BASE + "/" + id, memberToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getById(id).get("status")).isEqualTo("CANCELED");

        ResponseEntity<Map> confirm = post(BASE + "/" + id + "/confirm", Map.of(), memberToken, Map.class);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @Order(20)
    @DisplayName("Proposta de remarcação: horário antigo continua reservado e o proposto não reserva a agenda")
    void propostaMantemHorarioAntigoReservado() {
        LocalDate day = freshDay();
        Long id = memberAppointmentAt(day.atTime(10, 0));

        ResponseEntity<Map> proposal = post(BASE + "/" + id + "/reschedule-request",
                Map.of("newDate", iso(day.atTime(16, 0))), memberToken, Map.class);
        assertThat(proposal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(proposal.getBody().get("status")).isEqualTo("RESCHEDULE_REQUESTED");
        assertThat(proposal.getBody().get("scheduledAt").toString()).contains("T10:00");
        assertThat(proposal.getBody().get("proposedAt").toString()).contains("T16:00");

        // Horário antigo segue ocupado...
        assertThat(post(BASE, adminAppointment(day.atTime(10, 0), 30), adminToken, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // ...e o proposto NÃO é reservado: o admin ainda consegue agendar nele.
        assertThat(post(BASE, adminAppointment(day.atTime(16, 0), 30), adminToken, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(21)
    @DisplayName("POST /appointments/{id}/reschedule-approve → admin aprova e define a duração; fica CONFIRMED")
    void adminAprovaRemarcacaoDefinindoDuracao() {
        LocalDate day = freshDay();
        Long id = memberAppointmentAt(day.atTime(10, 0));
        post(BASE + "/" + id + "/reschedule-request", Map.of("newDate", iso(day.atTime(16, 0))), memberToken, Map.class);

        ResponseEntity<Map> approved = post(BASE + "/" + id + "/reschedule-approve",
                Map.of("durationMinutes", 45), adminToken, Map.class);

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("CONFIRMED");
        assertThat(approved.getBody().get("scheduledAt").toString()).contains("T16:00");
        assertThat(((Number) approved.getBody().get("durationMinutes")).intValue()).isEqualTo(45);
        assertThat(approved.getBody().get("proposedAt")).isNull();
    }

    @Test
    @Order(22)
    @DisplayName("POST /appointments/{id}/reschedule-reject → admin recusa e o agendamento volta ao original")
    void adminRecusaRemarcacao() {
        LocalDate day = freshDay();
        Long id = memberAppointmentAt(day.atTime(10, 0));
        post(BASE + "/" + id + "/reschedule-request", Map.of("newDate", iso(day.atTime(16, 0))), memberToken, Map.class);

        ResponseEntity<Map> rejected = post(BASE + "/" + id + "/reschedule-reject",
                Map.of("reason", "Sem agenda nesse horário"), adminToken, Map.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().get("status")).isEqualTo("SCHEDULED");
        assertThat(rejected.getBody().get("scheduledAt").toString()).contains("T10:00");
        assertThat(rejected.getBody().get("proposedAt")).isNull();
    }

    @Test
    @Order(23)
    @DisplayName("Recusar remarcação de um agendamento já confirmado devolve para CONFIRMED")
    void recusaPreservaConfirmacaoAnterior() {
        LocalDate day = freshDay();
        Long id = memberAppointmentAt(day.atTime(10, 0));
        post(BASE + "/" + id + "/confirm", Map.of(), memberToken, Map.class);
        post(BASE + "/" + id + "/reschedule-request", Map.of("newDate", iso(day.atTime(16, 0))), memberToken, Map.class);

        ResponseEntity<Map> rejected = post(BASE + "/" + id + "/reschedule-reject", Map.of(), adminToken, Map.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().get("status")).isEqualTo("CONFIRMED");
    }

    @Test
    @Order(24)
    @DisplayName("Aprovar com conflito → 422 explicando quem ocupa; admin aprova em outro horário")
    void conflitoNaAprovacaoPedeOutroHorario() {
        LocalDate day = freshDay();
        Long id = memberAppointmentAt(day.atTime(10, 0));
        post(BASE + "/" + id + "/reschedule-request", Map.of("newDate", iso(day.atTime(16, 0))), memberToken, Map.class);
        // Alguém ocupa o horário proposto depois do pedido.
        assertThat(post(BASE, adminAppointment(day.atTime(16, 0), 30), adminToken, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> conflict = post(BASE + "/" + id + "/reschedule-approve", Map.of(), adminToken, Map.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(conflict.getBody().get("message").toString()).contains("Conflito");
        assertThat(getById(id).get("status")).isEqualTo("RESCHEDULE_REQUESTED");

        ResponseEntity<Map> alternative = post(BASE + "/" + id + "/reschedule-approve",
                Map.of("newDate", iso(day.atTime(17, 0))), adminToken, Map.class);
        assertThat(alternative.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(alternative.getBody().get("scheduledAt").toString()).contains("T17:00");
    }

    @Test
    @Order(25)
    @DisplayName("Aprovar/recusar: só admin/funcionário, e só se houver pedido pendente")
    void aprovacaoExigePermissaoEPedidoPendente() {
        LocalDate day = freshDay();
        Long id = memberAppointmentAt(day.atTime(10, 0));

        ResponseEntity<Map> byMember = post(BASE + "/" + id + "/reschedule-approve", Map.of(), memberToken, Map.class);
        assertThat(byMember.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> noPending = post(BASE + "/" + id + "/reschedule-approve", Map.of(), adminToken, Map.class);
        assertThat(noPending.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @Order(26)
    @DisplayName("PATCH /appointments/{id}/status → admin não força RESCHEDULE_REQUESTED, mas pode marcar CONFIRMED")
    void adminMarcaConfirmadoMasNaoForcaProposta() {
        Long id = memberAppointmentAt(freshDay().atTime(10, 0));

        ResponseEntity<Map> forced = patch(BASE + "/" + id + "/status",
                Map.of("status", "RESCHEDULE_REQUESTED"), adminToken, Map.class);
        assertThat(forced.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> confirmed = patch(BASE + "/" + id + "/status",
                Map.of("status", "CONFIRMED"), adminToken, Map.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().get("confirmedAt")).isNotNull();
    }

    @Test
    @Order(27)
    @DisplayName("POST /appointments/{id}/confirm → admin \"mantém\" o agendamento sem remarcação pendente")
    void adminMantemAgendamento() {
        Long id = memberAppointmentAt(freshDay().atTime(10, 0));

        ResponseEntity<Map> kept = post(BASE + "/" + id + "/confirm", Map.of(), adminToken, Map.class);

        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(kept.getBody().get("status")).isEqualTo("CONFIRMED");
    }
}
