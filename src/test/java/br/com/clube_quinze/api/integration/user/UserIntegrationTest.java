package br.com.clube_quinze.api.integration.user;

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
 * Testes de integração para UserController.
 *
 * Cenários cobertos:
 *  - GET /users/ping
 *  - GET /users/me (perfil do usuário autenticado)
 *  - PUT /users/me (atualização de perfil)
 *  - GET /users/{id}
 *  - GET /users (listagem — admin/employee)
 *  - 401 sem autenticação
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Usuários — Testes de Integração")
class UserIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/users";
    private String memberToken;
    private String adminToken;

    @BeforeEach
    void setup() {
        adminToken = adminToken();

        // Registra membro para os testes
        String memberEmail = "user_it_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Membro Integração",
                "email", memberEmail,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic("/api/v1/auth/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        memberToken = (String) regRes.getBody().get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("GET /users/ping → 200 endpoint disponível")
    void devePingRetornarOk() {
        ResponseEntity<String> response = get(BASE + "/ping", memberToken, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("GET /users/me → 200 retorna perfil do usuário autenticado")
    void deveRetornarPerfilDoUsuarioAutenticado() {
        ResponseEntity<Map> response = get(BASE + "/me", memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody()).containsKey("email");
        assertThat(response.getBody()).containsKey("membershipTier");

    }

    @Test
    @Order(3)
    @DisplayName("GET /users/me → 401 sem autenticação")
    void deveRetornar401SemAutenticacao() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url(BASE + "/me"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(4)
    @DisplayName("PUT /users/me → 200 atualiza perfil com sucesso")
    void deveAtualizarPerfilDoUsuario() {
        ResponseEntity<Map> meResponse = get(BASE + "/me", memberToken, Map.class);
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> updateReq = Map.of(
                "name", "Nome Atualizado Integração",
            "email", meResponse.getBody().get("email"),
            "membershipTier", meResponse.getBody().get("membershipTier"),
            "phone", "11999998888"
        );

        ResponseEntity<Map> response = put(BASE + "/me", updateReq, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("name")).isEqualTo("Nome Atualizado Integração");
    }

    @Test
    @Order(5)
    @DisplayName("GET /users/{id} → 200 busca usuário por ID")
    void deveBuscarUsuarioPorId() {
        // Obtém ID do admin
        ResponseEntity<Map> meRes = get(BASE + "/me", adminToken, Map.class);
        assertThat(meRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long adminId = ((Number) meRes.getBody().get("id")).longValue();

        ResponseEntity<Map> response = get(BASE + "/" + adminId, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(adminId.intValue());
    }

    @Test
    @Order(6)
    @DisplayName("GET /users → 200 admin lista todos os membros")
    void deveAdminListarMembros() {
        ResponseEntity<Object> response = get(BASE, adminToken, Object.class);

        assertThat(response.getStatusCode())
                .withFailMessage("Resposta inesperada: status=%s body=%s", response.getStatusCode(), response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(List.class);
    }

    @Test
    @Order(7)
    @DisplayName("GET /users → 403 membro comum não pode listar todos usuários")
    void deveRetornar403QuandoMembroListaTodosUsuarios() {
        ResponseEntity<Map> response = get(BASE, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(8)
    @DisplayName("GET /users/{id} → 404 usuário inexistente")
    void deveRetornar404ParaUsuarioInexistente() {
        ResponseEntity<Map> response = get(BASE + "/999999", adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
