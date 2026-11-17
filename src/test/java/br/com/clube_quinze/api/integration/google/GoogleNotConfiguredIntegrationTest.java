package br.com.clube_quinze.api.integration.google;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Google Agenda — servidor sem credenciais configuradas")
@SuppressWarnings({"rawtypes", "unchecked"})
class GoogleNotConfiguredIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Estado NOT_CONFIGURED e conectar é recusado com mensagem clara; o resto do sistema segue normal")
    void integracaoDesligada() {
        String admin = adminToken();

        ResponseEntity<Map> status = get("/api/v1/admin/integrations/google", admin, Map.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().get("status")).isEqualTo("NOT_CONFIGURED");
        assertThat(status.getBody().get("configured")).isEqualTo(false);

        ResponseEntity<Map> connect = post("/api/v1/admin/integrations/google/connect", Map.of(), admin, Map.class);
        assertThat(connect.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(connect.getBody().get("message").toString()).contains("não está configurada");
    }
}
