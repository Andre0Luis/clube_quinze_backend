package br.com.clube_quinze.api.integration.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Actuator — versão publicada (usada na verificação pós-deploy)")
@SuppressWarnings({"rawtypes", "unchecked"})
class ActuatorInfoIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /actuator/info é público e informa a versão do pom (sem placeholder)")
    void infoPublicoComVersao() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/actuator/info"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> app = (Map<String, Object>) response.getBody().get("app");
        assertThat(app).isNotNull();
        assertThat(String.valueOf(app.get("version"))).matches("\\d+\\.\\d+\\.\\d+.*");
        // Sem APP_BUILD (testes e desenvolvimento) a build aparece como "unknown"; no deploy vira a tag da imagem.
        assertThat(String.valueOf(app.get("build"))).isEqualTo("unknown");
    }
}
