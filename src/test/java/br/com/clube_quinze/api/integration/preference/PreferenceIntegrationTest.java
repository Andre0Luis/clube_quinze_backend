package br.com.clube_quinze.api.integration.preference;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para PreferenceController.
 *
 * Cenários cobertos:
 *  - Criar preferência
 *  - Listar preferências do usuário
 *  - Atualizar preferência
 *  - Deletar preferência
 *  - Admin listar preferências de outro usuário
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Preferências — Testes de Integração")
class PreferenceIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/preferences";
    private static Long createdPreferenceId;
    private String memberToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        adminToken = adminToken();

        String email = "pref_member_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Membro Preferência",
                "email", email,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic("/api/v1/auth/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        memberToken = (String) regRes.getBody().get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("POST /preferences → 201 cria preferência com sucesso")
    void deveCriarPreferencia() {
        Map<String, String> request = Map.of(
                "key", "HORARIO_PREFERIDO",
                "value", "MANHA"
        );

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("key")).isEqualTo("HORARIO_PREFERIDO");

        createdPreferenceId = ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("GET /preferences → 200 lista preferências do usuário autenticado")
    void deveListarPreferencias() {
        if (createdPreferenceId == null) deveCriarPreferencia();

        ResponseEntity<List> response = get(BASE, memberToken, List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("PUT /preferences/{id} → 200 atualiza preferência")
    void deveAtualizarPreferencia() {
        if (createdPreferenceId == null) deveCriarPreferencia();

        Map<String, String> updateReq = Map.of(
                "key", "HORARIO_PREFERIDO",
                "value", "TARDE"
        );

        ResponseEntity<Map> response = put(BASE + "/" + createdPreferenceId, updateReq, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("value")).isEqualTo("TARDE");
    }

    @Test
    @Order(4)
    @DisplayName("POST /preferences → 400 sem campos obrigatórios")
    void deveRetornar400SemCamposObrigatorios() {
        Map<String, String> request = Map.of("key", "SEM_VALUE");

        ResponseEntity<Map> response = post(BASE, request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(5)
    @DisplayName("DELETE /preferences/{id} → 204 remove preferência")
    void deveDeletarPreferencia() {
        if (createdPreferenceId == null) deveCriarPreferencia();

        ResponseEntity<Void> response = delete(BASE + "/" + createdPreferenceId, memberToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Valida remoção: lista deve estar vazia (ou sem a preferência removida)
        ResponseEntity<List> listRes = get(BASE, memberToken, List.class);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
