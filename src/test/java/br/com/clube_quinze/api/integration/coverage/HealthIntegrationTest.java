package br.com.clube_quinze.api.integration.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("/health — aplicação, versão, build e conexões")
@SuppressWarnings({"rawtypes", "unchecked"})
class HealthIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /health é público e informa app, versão, build, ambiente e o estado de cada conexão")
    void healthPublicoComDetalhes() {
        long start = System.currentTimeMillis();

        ResponseEntity<Map> response = restTemplate.getForEntity(url("/health"), Map.class);

        // O RabbitMQ não existe nos testes: a verificação tem limite de 2 s e a API segue de pé (DEGRADED, não DOWN).
        assertThat(System.currentTimeMillis() - start).isLessThan(8_000);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = response.getBody();
        assertThat(body.get("status")).isEqualTo("DEGRADED");
        assertThat(body.get("app")).isEqualTo("clube-quinze");
        assertThat(String.valueOf(body.get("version"))).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(body.get("build")).isEqualTo("unknown"); // sem APP_BUILD fora da imagem
        assertThat(body.get("environment")).isEqualTo("test");
        assertThat(body.get("time")).isNotNull();
        assertThat(((Number) body.get("uptimeSeconds")).longValue()).isGreaterThanOrEqualTo(0);

        Map<String, Map> checks = (Map<String, Map>) body.get("checks");
        assertThat(checks.get("database").get("status")).isEqualTo("UP");
        assertThat(((Number) checks.get("database").get("responseMs")).longValue()).isGreaterThanOrEqualTo(0);
        assertThat(checks.get("rabbitmq").get("status")).isEqualTo("DOWN");
        assertThat(checks.get("push").get("status")).isEqualTo("NOT_CONFIGURED");
        assertThat(checks.get("googleCalendar").get("status")).isEqualTo("NOT_CONFIGURED");
        assertThat(checks).containsKeys("database", "rabbitmq", "push", "email", "googleCalendar");
    }

    @Test
    @DisplayName("/health não vaza endereço do banco, usuário, senha nem segredos")
    void healthNaoVazaSegredos() {
        String body = restTemplate.getForObject(url("/health"), String.class).toLowerCase();

        assertThat(body).doesNotContain("jdbc", "password", "senha", "secret", "token", "localhost:", "h2");
    }

    @Test
    @DisplayName("HEAD /health também é público (monitores de uptime usam HEAD) e não é cacheado")
    void healthHead() {
        ResponseEntity<Void> response = restTemplate.exchange(url("/health"), HttpMethod.HEAD,
                new HttpEntity<>(new HttpHeaders()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }
}
