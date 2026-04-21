package br.com.clube_quinze.api.integration;

import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.UserRepository;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Classe base para todos os testes de integração.
 * <p>
 * Sobe o servidor real em porta aleatória com Spring Security ativo.
 * O banco H2 em memória é criado via DDL do Hibernate (profile "test").
 * Cada classe filha pode usar @Transactional para rollback automático.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("rawtypes")
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

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

    protected String loginAsAdmin() {
        return adminToken();
    }

    protected String loginAsMember() {
        String email = "member_it_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        registerUser(email, "Membro Integração");
        return authenticate(email, "Senha@1234");
    }

    protected String loginAsEmployee() {
        String email = "employee_it_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        registerUser(email, "Funcionário Integração");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado após registro"));
        user.setRole(RoleType.CLUB_EMPLOYE);
        userRepository.save(user);
        return authenticate(email, "Senha@1234");
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

    private void registerUser(String email, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("email", email);
        payload.put("password", "Senha@1234");
        payload.put("membershipTier", "QUINZE_STANDARD");

        ResponseEntity<Map> response = postPublic("/api/v1/auth/register", payload, Map.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Falha ao registrar usuário de teste: " + response.getStatusCode());
        }
    }
}
