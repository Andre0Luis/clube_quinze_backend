package br.com.clube_quinze.api.integration.auth;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para AuthController.
 *
 * Cenários cobertos:
 *  - Registro de novo usuário
 *  - Login com credenciais válidas
 *  - Login com credenciais inválidas
 *  - Refresh de token
 *  - Logout
 *  - Alteração de senha
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auth — Testes de Integração")
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/auth";
    // Estado compartilhado entre testes da sequência
    private static String registeredEmail;
    private static String registeredRefreshToken;
    private static String registeredAccessToken;

    @Test
    @Order(1)
    @DisplayName("POST /auth/register → 201 cria usuário com sucesso")
    void deveRegistrarNovoUsuario() {
        registeredEmail = "integration_test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        Map<String, Object> request = Map.of(
                "name", "Usuário Integração",
                "email", registeredEmail,
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );

        ResponseEntity<Map> response = postPublic(BASE + "/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("accessToken")).isNotNull();
        assertThat(response.getBody().get("tokenType")).isEqualTo("Bearer");

        // Guarda tokens para testes subsequentes
        registeredAccessToken = (String) response.getBody().get("accessToken");
        registeredRefreshToken = (String) response.getBody().get("refreshToken");
    }

    @Test
    @Order(2)
    @DisplayName("POST /auth/login → 200 login com credenciais válidas")
    void deveRealizarLoginComSucesso() {
        Map<String, String> request = Map.of(
                "email", ADMIN_EMAIL,
                "password", ADMIN_PASSWORD
        );

        ResponseEntity<Map> response = postPublic(BASE + "/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        assertThat(response.getBody()).containsKey("refreshToken");
        assertThat((String) response.getBody().get("accessToken")).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("POST /auth/login → 401 credenciais incorretas")
    void deveRetornar401ParaCredenciaisInvalidas() {
        Map<String, String> request = Map.of(
                "email", "naoexiste@test.com",
                "password", "senhaerrada"
        );

        ResponseEntity<Map> response = postPublic(BASE + "/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(4)
    @DisplayName("POST /auth/register → 400 email duplicado")
    void deveRejeitarEmailDuplicado() {
        Map<String, Object> request = Map.of(
                "name", "Duplicado",
                "email", ADMIN_EMAIL, // email já existente
                "password", "Senha@1234",
                "membershipTier", "QUINZE_STANDARD"
        );

        ResponseEntity<Map> response = postPublic(BASE + "/register", request, Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @Order(5)
    @DisplayName("POST /auth/refresh → 200 renova token com refreshToken válido")
    void deveRenovarTokenComRefreshValido() {
        // Garante estado do registeredRefreshToken
        if (registeredRefreshToken == null) {
            deveRegistrarNovoUsuario();
        }

        Map<String, String> request = Map.of("refreshToken", registeredRefreshToken);

        ResponseEntity<Map> response = postPublic(BASE + "/refresh", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
    }

    @Test
    @Order(6)
    @DisplayName("POST /auth/logout → 204 invalida sessão do usuário")
    void deveRealizarLogoutComSucesso() {
        if (registeredRefreshToken == null) {
            deveRegistrarNovoUsuario();
        }

        Map<String, String> request = Map.of("refreshToken", registeredRefreshToken);

        ResponseEntity<Map> response = postPublic(BASE + "/logout", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(7)
    @DisplayName("POST /auth/register → 400 sem campos obrigatórios")
    void deveRetornar400SemCamposObrigatorios() {
        Map<String, Object> request = Map.of(
                "name", "Incompleto"
                // faltam email, password, membershipTier
        );

        ResponseEntity<Map> response = postPublic(BASE + "/register", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(8)
    @DisplayName("PUT /auth/change-password → 204 altera senha com sucesso")
    void deveAlterarSenhaComSucesso() {
        // Registra usuário fresco para alterar a senha
        String email = "change_pwd_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        Map<String, Object> regReq = Map.of(
                "name", "Change Pass User",
                "email", email,
                "password", "SenhaAntiga@1",
                "membershipTier", "QUINZE_STANDARD"
        );
        ResponseEntity<Map> regRes = postPublic(BASE + "/register", regReq, Map.class);
        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = (String) regRes.getBody().get("accessToken");

        Map<String, String> changePwd = Map.of(
                "currentPassword", "SenhaAntiga@1",
                "newPassword", "SenhaNova@99"
        );
        ResponseEntity<Void> response = put(BASE + "/change-password", changePwd, token, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
