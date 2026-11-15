package br.com.clube_quinze.api.integration.migration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.GoogleCalendarConnectionRepository;
import br.com.clube_quinze.api.model.google.GoogleCalendarConnection;
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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private GoogleCalendarConnectionRepository googleConnections;

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
                Map.of("newDate", day.atTime(16, 0).format(iso), "durationMinutes", 45), adminToken, Map.class);
        assertThat(rescheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rescheduled.getBody().get("scheduledAt").toString()).contains("T16:00");
        assertThat(((Number) rescheduled.getBody().get("durationMinutes")).intValue()).isEqualTo(45);

        ResponseEntity<Map> completed = patch("/api/v1/appointments/" + id + "/status",
                Map.of("status", "COMPLETED"), adminToken, Map.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Fluxo de confirmação e remarcação grava e lê confirmed_at/proposed_at (migration V16)")
    void fluxoDeConfirmacaoGravaNasColunasNovas() {
        String adminToken = adminToken();
        String email = "mariadb_confirm_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        ResponseEntity<Map> reg = postPublic("/api/v1/auth/register",
                Map.of("name", "Membro Confirma", "email", email, "password", "Senha@1234",
                        "membershipTier", "QUINZE_STANDARD"), Map.class);
        String memberToken = (String) reg.getBody().get("accessToken");
        Long memberId = ((Number) get("/api/v1/users/me", memberToken, Map.class).getBody().get("id")).longValue();

        LocalDate day = LocalDate.now().plusDays(310);
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        ResponseEntity<Map> created = post("/api/v1/appointments", Map.of(
                "clientId", memberId, "scheduledAt", day.atTime(10, 0).format(iso),
                "appointmentTier", "QUINZE_STANDARD", "serviceType", "Corte confirmação"), memberToken, Map.class);
        Long id = ((Number) created.getBody().get("id")).longValue();

        ResponseEntity<Map> proposal = post("/api/v1/appointments/" + id + "/reschedule-request",
                Map.of("newDate", day.atTime(16, 0).format(iso)), memberToken, Map.class);
        assertThat(proposal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(proposal.getBody().get("status")).isEqualTo("RESCHEDULE_REQUESTED");
        assertThat(proposal.getBody().get("proposedAt")).isNotNull();

        ResponseEntity<Map> approved = post("/api/v1/appointments/" + id + "/reschedule-approve",
                Map.of("durationMinutes", 45), adminToken, Map.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("CONFIRMED");
        assertThat(approved.getBody().get("confirmedAt")).isNotNull();
        assertThat(approved.getBody().get("proposedAt")).isNull();

        // O que foi gravado é o que se lê de volta.
        ResponseEntity<Map> reloaded = get("/api/v1/appointments/" + id, adminToken, Map.class);
        assertThat(reloaded.getBody().get("status")).isEqualTo("CONFIRMED");
        assertThat(reloaded.getBody().get("scheduledAt").toString()).contains("T16:00");
    }

    @Test
    @DisplayName("Migration V17 (Google Agenda): tabela da conexão e colunas do agendamento funcionam de verdade")
    void integracaoGoogleGravaELeNoMariaDb() {
        // Conexão com refresh token longo (criptografado) numa coluna VARCHAR(2000).
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        GoogleCalendarConnection c = new GoogleCalendarConnection();
        c.setConnectedByUserId(1L);
        c.setGoogleEmail("quinze@gmail.com");
        c.setRefreshTokenEnc("x".repeat(1500));
        c.setStatus(GoogleCalendarConnection.Status.READY);
        c.setCalendarId("cal@group.calendar.google.com");
        Long id = tx.execute(status -> googleConnections.save(c).getId());
        assertThat(googleConnections.findById(id).orElseThrow().getRefreshTokenEnc()).hasSize(1500);
        tx.executeWithoutResult(status -> googleConnections.deleteAll());

        // As duas colunas novas do agendamento e a query que grava o vínculo sem mexer em atualizado_em.
        String adminToken = adminToken();
        String email = "mariadb_google_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        ResponseEntity<Map> reg = postPublic("/api/v1/auth/register",
                Map.of("name", "Membro Google", "email", email, "password", "Senha@1234",
                        "membershipTier", "QUINZE_STANDARD"), Map.class);
        String memberToken = (String) reg.getBody().get("accessToken");
        Long memberId = ((Number) get("/api/v1/users/me", memberToken, Map.class).getBody().get("id")).longValue();
        ResponseEntity<Map> created = post("/api/v1/appointments", Map.of(
                "clientId", memberId,
                "scheduledAt", LocalDate.now().plusDays(320).atTime(10, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD", "serviceType", "Corte Google"), memberToken, Map.class);
        Long appointmentId = ((Number) created.getBody().get("id")).longValue();
        assertThat(adminToken).isNotBlank();

        var before = appointmentRepository.findById(appointmentId).orElseThrow();
        tx.executeWithoutResult(status ->
                appointmentRepository.markGoogleSynced(appointmentId, "evento-123", before.getUpdatedAt()));
        var after = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(after.getGoogleEventId()).isEqualTo("evento-123");
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(jdbc.queryForObject("select google_event_id from agendamentos where id = ?", String.class, appointmentId))
                .isEqualTo("evento-123");

        tx.executeWithoutResult(status -> appointmentRepository.resetGoogleSync());
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getGoogleEventId()).isNull();
    }
}
