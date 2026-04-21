package br.com.clube_quinze.api.integration.plan;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para PlanController.
 *
 * Cenários cobertos:
 *  - Criação de plano (apenas admin)
 *  - Listagem de planos (pública)
 *  - Busca por ID
 *  - Atualização (apenas admin)
 *  - Tentativa de criação por membro (403)
 *  - Remoção (apenas admin)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Planos — Testes de Integração")
class PlanIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/plans";
    private static Long createdPlanId;
    private String adminToken;

    @BeforeEach
    void setup() {
        adminToken = adminToken();
    }

    private Map<String, Object> buildPlanRequest(String name) {
        return Map.of(
                "name", name,
                "description", "Plano de teste integração",
                "price", new BigDecimal("199.90"),
                "durationMonths", 1
        );
    }

    @Test
    @Order(1)
    @DisplayName("GET /plans → 200 lista planos sem autenticação")
    void deveListarPlanosPublicamente() {
        ResponseEntity<List> response = restTemplate.getForEntity(url(BASE), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("POST /plans → 201 admin cria plano")
    void deveAdminCriarPlano() {
        String planName = "Plano Integracao " + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> request = buildPlanRequest(planName);

        ResponseEntity<Map> response = post(BASE, request, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("name")).isEqualTo(planName);

        createdPlanId = ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    @Order(3)
    @DisplayName("GET /plans/{id} → 200 busca plano por ID")
    void deveBuscarPlanoPorId() {
        if (createdPlanId == null) deveAdminCriarPlano();

        ResponseEntity<Map> response = get(BASE + "/" + createdPlanId, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(createdPlanId.intValue());
    }

    @Test
    @Order(4)
    @DisplayName("PUT /plans/{id} → 200 admin atualiza plano")
    void deveAdminAtualizarPlano() {
        if (createdPlanId == null) deveAdminCriarPlano();

        Map<String, Object> updateRequest = Map.of(
                "name", "Plano Atualizado " + UUID.randomUUID().toString().substring(0, 6),
                "description", "Descrição atualizada",
                "price", new BigDecimal("249.90"),
                "durationMonths", 3
        );

        ResponseEntity<Map> response = put(BASE + "/" + createdPlanId, updateRequest, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("durationMonths")).isEqualTo(3);
    }

    @Test
    @Order(5)
    @DisplayName("POST /plans → 403 membro comum não pode criar plano")
    void deveRetornar403ParaMembroComum() {
        // Registra um usuário membro comum
        String memberEmail = "member_plan_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Membro Teste Plan",
                "email", memberEmail,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic("/api/v1/auth/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String memberToken = (String) regRes.getBody().get("accessToken");

        Map<String, Object> request = buildPlanRequest(
                "Plano Proibido " + UUID.randomUUID().toString().substring(0, 6));

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(6)
    @DisplayName("GET /plans/{id} → 404 plano inexistente")
    void deveRetornar404ParaPlanoInexistente() {
        ResponseEntity<Map> response = get(BASE + "/999999", adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /plans/{id} → 204 admin remove plano")
    void deveAdminRemoverPlano() {
        if (createdPlanId == null) deveAdminCriarPlano();

        ResponseEntity<Void> response = delete(BASE + "/" + createdPlanId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Valida que o plano foi removido
        ResponseEntity<Map> getResponse = get(BASE + "/" + createdPlanId, adminToken, Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
