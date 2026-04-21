package br.com.clube_quinze.api.integration.feedback;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para FeedbackController.
 *
 * Pré-requisito: cria membro + agendamento (COMPLETED) antes dos testes.
 *
 * Cenários cobertos:
 *  - Envio de feedback para agendamento concluído
 *  - Listagem dos próprios feedbacks
 *  - Listagem geral (admin)
 *  - Média por serviço (admin)
 *  - Média por usuário (admin)
 *  - 400 feedback duplicado
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Feedback — Testes de Integração")
class FeedbackIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/feedbacks";
    private String memberToken;
    private Long memberId;
    private Long completedAppointmentId;
    private String adminToken;

    @BeforeEach
    void setup() {
        adminToken = adminToken();
        prepareMemberAndAppointment();
    }

    /**
     * Cria membro, cria agendamento e seta status COMPLETED via admin.
     * Necessário pois só se pode dar feedback em agendamentos concluídos.
     */
    private void prepareMemberAndAppointment() {
        // Registra membro
        String email = "fb_member_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Membro Feedback",
                "email", email,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic("/api/v1/auth/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        memberToken = (String) regRes.getBody().get("accessToken");

        // Obtem ID do membro
        ResponseEntity<Map> meRes = get("/api/v1/users/me", memberToken, Map.class);
        memberId = ((Number) meRes.getBody().get("id")).longValue();

        // Cria agendamento futuro
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0);
        Map<String, Object> apptReq = Map.of(
                "clientId", memberId,
                "scheduledAt", scheduledAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD",
                "serviceType", "Feedback Test Service",
                "durationMinutes", 60
        );
        ResponseEntity<Map> apptRes = post("/api/v1/appointments", apptReq, memberToken, Map.class);
        assertThat(apptRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        completedAppointmentId = ((Number) apptRes.getBody().get("id")).longValue();

        // Marca como COMPLETED via admin (pré-requisito para feedback)
        Map<String, String> statusReq = Map.of("status", "COMPLETED");
        ResponseEntity<Map> statusRes = patch(
                "/api/v1/appointments/" + completedAppointmentId + "/status",
                statusReq, adminToken, Map.class);
        assertThat(statusRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(1)
    @DisplayName("POST /feedbacks → 201 membro envia feedback de agendamento concluído")
    void deveEnviarFeedback() {
        Map<String, Object> request = Map.of(
                "appointmentId", completedAppointmentId,
                "rating", 5,
                "comment", "Excelente atendimento! Teste de integração."
        );

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("rating")).isEqualTo(5);
    }

    @Test
    @Order(2)
    @DisplayName("GET /feedbacks/me → 200 lista feedbacks do próprio membro")
    void deveListarMeusFeedbacks() {
        // Garante que existe feedback criado
        deveEnviarFeedback();

        ResponseEntity<Map> response = get(BASE + "/me", memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @Order(3)
    @DisplayName("GET /feedbacks → 200 admin lista todos os feedbacks")
    void deveAdminListarTodosFeedbacks() {
        ResponseEntity<Map> response = get(BASE, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    @Order(4)
    @DisplayName("GET /feedbacks → 403 membro não pode listar todos os feedbacks")
    void deveRetornar403QuandoMembroListaTodos() {
        ResponseEntity<Map> response = get(BASE, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    @DisplayName("GET /feedbacks/averages/services → 200 admin busca médias por serviço")
    void deveAdminBuscarMediasPorServico() {
        ResponseEntity<List> response = get(BASE + "/averages/services", adminToken, List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(6)
    @DisplayName("GET /feedbacks/averages/users/{id} → 200 admin busca média por usuário")
    void deveAdminBuscarMediaPorUsuario() {
        ResponseEntity<Object> response = get(BASE + "/averages/users/" + memberId, adminToken, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(7)
    @DisplayName("POST /feedbacks → 400 rating inválido (fora de 1-5)")
    void deveRetornar400RatingInvalido() {
        Map<String, Object> request = Map.of(
                "appointmentId", completedAppointmentId,
                "rating", 10, // inválido: máx é 5
                "comment", "Rating inválido"
        );

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
