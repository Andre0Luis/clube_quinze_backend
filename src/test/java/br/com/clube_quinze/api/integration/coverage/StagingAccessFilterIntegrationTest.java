package br.com.clube_quinze.api.integration.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.staging.access-key=chave-de-teste-123",
        "app.staging.public-paths=/appointment-action,/integrations/google/callback"})
@DisplayName("Homologação — porta de entrada com chave de acesso")
class StagingAccessFilterIntegrationTest extends AbstractIntegrationTest {

    private ResponseEntity<String> fetch(String path, String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        if (headerName != null) {
            headers.set(headerName, headerValue);
        }
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("Sem chave ou com chave errada: 404, como se o endereço não existisse")
    void semChaveRecebe404() {
        assertThat(fetch("/api/v1/plans", null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fetch("/api/v1/plans", "X-Staging-Key", "errada").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fetch("/swagger-ui.html", null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fetch("/actuator/info", null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Com a chave no cabeçalho a API responde normalmente")
    void chaveNoCabecalho() {
        ResponseEntity<String> response = fetch("/api/v1/plans", "X-Staging-Key", "chave-de-teste-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Chave no parâmetro libera e grava o cookie; com o cookie as próximas chamadas passam")
    void chaveNoParametroGravaCookie() {
        ResponseEntity<String> first = fetch("/api/v1/plans?staging_key=chave-de-teste-123", null, null);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = first.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("staging_key=chave-de-teste-123").contains("HttpOnly");

        ResponseEntity<String> withCookie = fetch("/api/v1/plans", HttpHeaders.COOKIE, "staging_key=chave-de-teste-123");
        assertThat(withCookie.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Links de e-mail abrem sem chave (caminho público configurado)")
    void caminhoPublicoPassaSemChave() {
        ResponseEntity<String> response = fetch("/appointment-action?token=invalido", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("inválido ou expirado");
    }
}
