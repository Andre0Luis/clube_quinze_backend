package br.com.clube_quinze.api.integration.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Política de privacidade — exigências do Google (login OAuth do Google Agenda)")
class PublicPoliciesIntegrationTest extends AbstractIntegrationTest {

    private static final String[] EXIGIDOS = {
            "Integração com o Google Agenda",
            "Uso Limitado",
            "Política de Dados do Usuário dos Serviços de API do Google",
            "myaccount.google.com/permissions"};

    @Test
    @DisplayName("A política servida pela API (usada no app) é pública e traz a seção do Google")
    void politicaDaApiTrazSecaoDoGoogle() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/public/policies/privacy"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(EXIGIDOS);
    }

    @Test
    @DisplayName("A cópia servida pelo nginx (/privacy-policy) tem as mesmas exigências")
    void copiaDoNginxTrazSecaoDoGoogle() throws Exception {
        String html = Files.readString(Path.of("nginx/policies/privacy.html"), StandardCharsets.UTF_8);

        assertThat(html).contains(EXIGIDOS);
    }

    @Test
    @DisplayName("A página inicial pública diz o que é o app, cita o Google Agenda e liga à política")
    void paginaInicialExplicaOApp() throws Exception {
        String html = Files.readString(Path.of("nginx/policies/home.html"), StandardCharsets.UTF_8);

        assertThat(html).contains("<title>Clube Quinze", "<h1>Clube Quinze</h1>", "Para que serve o aplicativo",
                "Integração com o Google Agenda", "href=\"/privacy-policy\"");
    }
}
