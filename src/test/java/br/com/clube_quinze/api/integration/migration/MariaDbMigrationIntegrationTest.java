package br.com.clube_quinze.api.integration.migration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe a aplicação contra um MariaDB REAL: o Flyway aplica todas as migrations (V1..Vn) e o Hibernate valida
 * as entidades contra o schema (ddl-auto=validate), exatamente como em produção. Os demais testes usam H2 com
 * o schema gerado pelo Hibernate e, por isso, nunca exercitam a sintaxe das migrations.
 *
 * Roda no job "Migrations (MariaDB real)" do CI. No CI (variável CI definida) ele NUNCA é ignorado: sem Docker o
 * teste falha, para o job não ficar verde sem ter validado nada. Localmente é ignorado se não houver Docker.
 * A versão da imagem pode ser trocada com -Dmariadb.image=mariadb:10.11 para igualar à de produção.
 */
@Tag("mariadb")
@EnabledIf("br.com.clube_quinze.api.integration.migration.MariaDbMigrationIntegrationTest#shouldRun")
@DisplayName("Migrations no MariaDB real")
@SuppressWarnings({"rawtypes", "unchecked"})
class MariaDbMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final String DB_NAME = "clube_quinze"; // spring.flyway.schemas aponta para este schema

    static MariaDBContainer<?> mariadb;

    /** No CI sempre roda (e falha sem Docker); fora dele, só se houver Docker. */
    static boolean shouldRun() {
        return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        mariadb = new MariaDBContainer<>(DockerImageName.parse(System.getProperty("mariadb.image", "mariadb:11.4")))
                .withDatabaseName(DB_NAME);
        mariadb.start(); // se o Docker não existir, falha aqui — de propósito
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    @DisplayName("Flyway aplica tudo, o Hibernate valida o schema e o fluxo básico de agendamento funciona")
    void migrationsAplicamEFluxoBasicoFunciona() {
        // Se o contexto subiu, o Flyway aplicou V1..Vn e o ddl-auto=validate aceitou o mapeamento das entidades.
        String adminToken = adminToken();

        String email = "mariadb_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        ResponseEntity<Map> reg = postPublic("/api/v1/auth/register",
                Map.of("name", "Membro MariaDB", "email", email, "password", "Senha@1234",
                        "membershipTier", "QUINZE_STANDARD"), Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String memberToken = (String) reg.getBody().get("accessToken");
        Long memberId = ((Number) get("/api/v1/users/me", memberToken, Map.class).getBody().get("id")).longValue();

        LocalDate day = LocalDate.now().plusDays(300);
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        ResponseEntity<Map> created = post("/api/v1/appointments", Map.of(
                "clientId", memberId, "scheduledAt", day.atTime(10, 0).format(iso),
                "appointmentTier", "QUINZE_STANDARD", "serviceType", "Corte MariaDB"), memberToken, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = ((Number) created.getBody().get("id")).longValue();

        // Leitura e escrita de verdade no MariaDB (tipos de data, enums gravados como texto, duração).
        ResponseEntity<Map> rescheduled = put("/api/v1/appointments/" + id + "/reschedule",
                Map.of("newDate", day.atTime(16, 0).format(iso)), adminToken, Map.class);
        assertThat(rescheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rescheduled.getBody().get("scheduledAt").toString()).contains("T16:00");

        ResponseEntity<Map> completed = patch("/api/v1/appointments/" + id + "/status",
                Map.of("status", "COMPLETED"), adminToken, Map.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");
    }
}
