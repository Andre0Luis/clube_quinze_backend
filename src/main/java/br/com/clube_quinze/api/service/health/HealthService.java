package br.com.clube_quinze.api.service.health;

import br.com.clube_quinze.api.dto.health.HealthResponse;
import br.com.clube_quinze.api.dto.health.HealthResponse.Check;
import br.com.clube_quinze.api.service.google.GoogleCalendarService;
import com.google.firebase.FirebaseApp;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Monta o /health público: qual aplicação, qual versão/build e se as conexões estão de pé (banco e fila de verdade;
 * push, e-mail e Google só informam se estão configurados, sem chamar serviços externos).
 *
 * Cada verificação real roda com limite de 2 s e resultado em cache curto, para um monitor que consulta toda hora não
 * acumular conexões presas quando um serviço cai.
 */
@Service
public class HealthService {

    static final long TIMEOUT_MS = 2000;

    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;
    private final GoogleCalendarService googleCalendarService;
    private final Environment environment;
    private final String appName;
    private final String version;
    private final String build;
    private final boolean firebaseEnabled;

    // Uma thread por verificação: se um serviço trava, só ela fica presa (nunca o servidor web).
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "health-probe");
        thread.setDaemon(true);
        return thread;
    });
    private final Probe databaseProbe = new Probe(this::probeDatabase, 5_000);
    private final Probe rabbitProbe = new Probe(this::probeRabbit, 15_000);

    public HealthService(DataSource dataSource, ConnectionFactory rabbitConnectionFactory,
                         GoogleCalendarService googleCalendarService, Environment environment,
                         @Value("${spring.application.name:clube-quinze}") String appName,
                         @Value("${info.app.version:unknown}") String version,
                         @Value("${info.app.build:unknown}") String build,
                         @Value("${firebase.enabled:true}") boolean firebaseEnabled) {
        this.dataSource = dataSource;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.googleCalendarService = googleCalendarService;
        this.environment = environment;
        this.appName = appName;
        this.version = version;
        this.build = build;
        this.firebaseEnabled = firebaseEnabled;
    }

    public HealthResponse check() {
        Map<String, Check> checks = new LinkedHashMap<>();
        Check database = databaseProbe.run();
        Check rabbit = rabbitProbe.run();
        checks.put("database", database);
        checks.put("rabbitmq", rabbit);
        checks.put("push", pushCheck());
        checks.put("email", emailCheck());
        checks.put("googleCalendar", googleCheck());

        // Banco fora = a API não trabalha (DOWN). Fila fora = notificações param, mas o app segue de pé (DEGRADED).
        String status = "DOWN".equals(database.status()) ? "DOWN" : "DOWN".equals(rabbit.status()) ? "DEGRADED" : "UP";
        String[] profiles = environment.getActiveProfiles();
        return new HealthResponse(status, appName, version, build,
                profiles.length > 0 ? profiles[0] : "default",
                Instant.now(), ManagementFactory.getRuntimeMXBean().getUptime() / 1000, checks);
    }

    private Check probeDatabase() {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? Check.up(elapsedMs(start)) : Check.down(elapsedMs(start), "conexão inválida");
        } catch (Exception ex) {
            return Check.down(elapsedMs(start), "sem conexão");
        }
    }

    private Check probeRabbit() {
        long start = System.nanoTime();
        try {
            org.springframework.amqp.rabbit.connection.Connection connection = rabbitConnectionFactory.createConnection();
            try {
                return connection.isOpen() ? Check.up(elapsedMs(start)) : Check.down(elapsedMs(start), "conexão fechada");
            } finally {
                connection.close();
            }
        } catch (Exception ex) {
            return Check.down(elapsedMs(start), "sem conexão");
        }
    }

    private Check pushCheck() {
        if (!firebaseEnabled) {
            return Check.notConfigured("desligado neste ambiente");
        }
        return FirebaseApp.getApps().isEmpty() ? Check.down(0L, "não inicializado") : Check.up(0L);
    }

    private Check emailCheck() {
        boolean brevo = environment.getProperty("app.brevo.enabled", Boolean.class, false);
        String host = environment.getProperty("spring.mail.host", "");
        if (brevo) {
            return new Check("CONFIGURED", null, "Brevo");
        }
        return host.isBlank() || "localhost".equals(host)
                ? Check.notConfigured("sem provedor de e-mail")
                : new Check("CONFIGURED", null, "SMTP");
    }

    private Check googleCheck() {
        try {
            String state = googleCalendarService.status().status();
            return "NOT_CONFIGURED".equals(state) ? Check.notConfigured("integração desligada") : new Check("CONFIGURED", null, state);
        } catch (Exception ex) {
            return Check.down(0L, "falha ao consultar");
        }
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /** Verificação com limite de tempo, resultado em cache curto e no máximo uma tentativa em andamento. */
    private final class Probe {
        private final Supplier<Check> action;
        private final long ttlMillis;
        private Check last;
        private long lastAt;
        private CompletableFuture<Check> inFlight;

        Probe(Supplier<Check> action, long ttlMillis) {
            this.action = action;
            this.ttlMillis = ttlMillis;
        }

        synchronized Check run() {
            long now = System.currentTimeMillis();
            if (last != null && now - lastAt < ttlMillis) {
                return last;
            }
            if (inFlight == null || inFlight.isDone()) {
                inFlight = CompletableFuture.supplyAsync(action, executor);
            }
            try {
                last = inFlight.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                lastAt = System.currentTimeMillis();
                return last;
            } catch (TimeoutException ex) {
                return Check.down(TIMEOUT_MS, "sem resposta em 2 s");
            } catch (Exception ex) {
                return Check.down(0L, "falha na verificação");
            }
        }
    }
}
