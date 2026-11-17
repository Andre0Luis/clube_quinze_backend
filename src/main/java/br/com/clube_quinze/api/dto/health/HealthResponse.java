package br.com.clube_quinze.api.dto.health;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Estado público da aplicação. status: UP (tudo ok), DEGRADED (algo não essencial fora: o app segue de pé) ou DOWN (o banco
 * de dados está fora: a API não consegue trabalhar). Nunca traz endereços, usuários, senhas nem versões internas.
 */
public record HealthResponse(
        String status,
        String app,
        String version,
        String build,
        String environment,
        Instant time,
        long uptimeSeconds,
        Map<String, Check> checks) implements Serializable {

    /** Resultado de uma verificação. status: UP, DOWN ou NOT_CONFIGURED (recurso opcional desligado de propósito). */
    public record Check(String status, Long responseMs, String detail) implements Serializable {
        public static Check up(long ms) {
            return new Check("UP", ms, null);
        }

        public static Check down(long ms, String detail) {
            return new Check("DOWN", ms, detail);
        }

        public static Check notConfigured(String detail) {
            return new Check("NOT_CONFIGURED", null, detail);
        }
    }
}
