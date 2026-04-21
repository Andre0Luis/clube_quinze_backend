package br.com.clube_quinze.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

/**
 * Classe base para todos os testes de integração.
 * <p>
 * Sobe o servidor real em porta aleatória com Spring Security ativo.
 * O banco H2 em memória é criado via DDL do Hibernate (profile "test").
 * Cada classe filha pode usar @Transactional para rollback automático.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    // ── Credenciais de seed (criadas pelo DatabaseSeeder no startup) ──────────
    protected static final String ADMIN_EMAIL    = "aluis283@gmail.com";
    protected static final String ADMIN_PASSWORD = "luizinho@01";

    // ── Build URL base ────────────────────────────────────────────────────────

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    /**
     * Autentica e retorna o accessToken JWT.
     */
    protected String authenticate(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"), body, Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (String) response.getBody().get("accessToken");
        }
        throw new IllegalStateException(
                "Falha ao autenticar como " + email + ". Status: " + response.getStatusCode()
                        + " Body: " + response.getBody());
    }

    /** Retorna token do admin seed. */
    protected String adminToken() {
        return authenticate(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    // ── Header helpers ────────────────────────────────────────────────────────

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ── Request helpers ───────────────────────────────────────────────────────

    protected <T> ResponseEntity<T> get(String path, String token, Class<T> responseType) {
        return restTemplate.exchange(
                url(path), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), responseType);
    }

    protected <T> ResponseEntity<T> post(String path, Object body, String token, Class<T> responseType) {
        return restTemplate.exchange(
                url(path), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), responseType);
    }

    protected <T> ResponseEntity<T> postPublic(String path, Object body, Class<T> responseType) {
        return restTemplate.exchange(
                url(path), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), responseType);
    }

    protected <T> ResponseEntity<T> put(String path, Object body, String token, Class<T> responseType) {
        return restTemplate.exchange(
                url(path), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), responseType);
    }

    protected <T> ResponseEntity<T> patch(String path, Object body, String token, Class<T> responseType) {
        return restTemplate.exchange(
                url(path), HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(token)), responseType);
    }

    protected ResponseEntity<Void> delete(String path, String token) {
        return restTemplate.exchange(
                url(path), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Void.class);
    }
}
