package br.com.clube_quinze.api.integration.coverage;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cobertura de Endpoints — Testes de Integração")
@SuppressWarnings({"rawtypes", "unchecked"})
class EndpointCoverageIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/v1/public/policies/privacy -> 200 com HTML")
    void deveRetornarPoliticaDePrivacidadePublica() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/api/v1/public/policies/privacy"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody()).contains("Política de Privacidade");
    }

    @Test
    @DisplayName("GET /reset-password -> 200 com página HTML")
    void deveServirPaginaResetPassword() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/reset-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsIgnoringCase("reset");
    }

    @Test
    @DisplayName("GET /api/v1/payments/renewals -> 200 para admin")
    void devePermitirAdminListarRenovacoes() {
        String adminToken = loginAsAdmin();

        ResponseEntity<Object> response = get("/api/v1/payments/renewals?windowDays=60", adminToken, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /api/v1/payments/renewals -> 403 para membro")
    void deveNegarMembroNaListagemDeRenovacoes() {
        String memberToken = loginAsMember();

        ResponseEntity<Map> response = get("/api/v1/payments/renewals", memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/tokens -> 200 para membro autenticado")
    void deveRegistrarPushToken() {
        String memberToken = loginAsMember();
        Map<String, Object> request = Map.of(
                "token", "token_it_" + System.nanoTime(),
                "platform", "ANDROID"
        );

        ResponseEntity<Void> response = post("/api/v1/notifications/tokens", request, memberToken, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("POST /api/v1/media/upload -> 401 sem autenticação")
    void deveBloquearUploadMidiaSemAutenticacao() {
        ResponseEntity<Map> response = postPublic("/api/v1/media/upload", Map.of(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/v1/community/posts/upload -> 401 sem autenticação")
    void deveBloquearUploadPostSemAutenticacao() {
        ResponseEntity<Map> response = postPublic("/api/v1/community/posts/upload", Map.of(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/upload -> 401 sem autenticação")
    void deveBloquearUploadPerfilSemAutenticacao() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/users/me/upload"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of(), jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("PUT /api/v1/users/{id}/upload -> 401 sem autenticação")
    void deveBloquearUploadPerfilPorIdSemAutenticacao() {
        String memberToken = loginAsMember();
        ResponseEntity<Map> me = get("/api/v1/users/me", memberToken, Map.class);
        Long memberId = ((Number) me.getBody().get("id")).longValue();

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/users/" + memberId + "/upload"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of(), jsonHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/plan -> 403 para membro")
    void deveNegarMembroAlterarPlanoDeOutroUsuario() {
        String memberToken = loginAsMember();
        String adminToken = loginAsAdmin();

        ResponseEntity<Map> adminMe = get("/api/v1/users/me", adminToken, Map.class);
        Long adminId = ((Number) adminMe.getBody().get("id")).longValue();

        Map<String, Object> request = Map.of(
                "membershipTier", "QUINZE_STANDARD",
                "durationMonths", 1
        );

        ResponseEntity<Map> response = post("/api/v1/users/" + adminId + "/plan", request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/plan/renew -> 403 para membro")
    void deveNegarMembroRenovarPlanoDeOutroUsuario() {
        String memberToken = loginAsMember();
        String adminToken = loginAsAdmin();

        ResponseEntity<Map> adminMe = get("/api/v1/users/me", adminToken, Map.class);
        Long adminId = ((Number) adminMe.getBody().get("id")).longValue();

        Map<String, Object> request = Map.of("durationMonths", 1);

        ResponseEntity<Map> response = post("/api/v1/users/" + adminId + "/plan/renew", request, memberToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{id} -> 204 admin remove usuário")
    void devePermitirAdminDeletarUsuario() {
        String memberToken = loginAsMember();
        String adminToken = loginAsAdmin();

        ResponseEntity<Map> memberMe = get("/api/v1/users/me", memberToken, Map.class);
        Long memberId = ((Number) memberMe.getBody().get("id")).longValue();

        ResponseEntity<Void> response = delete("/api/v1/users/" + memberId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password -> 204 para email inexistente")
    void deveAceitarForgotPasswordParaEmailInexistente() {
        Map<String, Object> request = Map.of("email", "missing_" + System.nanoTime() + "@test.com");

        ResponseEntity<Void> response = postPublic("/api/v1/auth/forgot-password", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password -> erro 4xx para token inválido")
    void deveRejeitarResetPasswordComTokenInvalido() {
        Map<String, Object> request = Map.of(
                "token", "token-invalido-it",
                "newPassword", "NovaSenha@123"
        );

        ResponseEntity<Map> response = postPublic("/api/v1/auth/reset-password", request, Map.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
