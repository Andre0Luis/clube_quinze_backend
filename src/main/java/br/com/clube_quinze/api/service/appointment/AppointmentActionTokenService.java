package br.com.clube_quinze.api.service.appointment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Gera e valida tokens assinados (HMAC-SHA256) usados nos botões do e-mail de confirmação.
 * O token carrega (agendamento, ação, expiração) e não precisa de tabela: a ação em si é idempotente
 * e só vale no estado correto do agendamento, então repetir o link não causa efeito extra.
 */
@Service
public class AppointmentActionTokenService {

    public enum Action { CONFIRM, CANCEL, RESCHEDULE }

    public record ParsedToken(Long appointmentId, Action action) {}

    private final byte[] key;

    public AppointmentActionTokenService(@Value("${app.security.jwt.secret}") String secret) {
        // Chave derivada: o mesmo segredo do JWT nunca assina dois usos diferentes diretamente.
        this.key = hmac(secret.getBytes(StandardCharsets.UTF_8), "appointment-action-v1".getBytes(StandardCharsets.UTF_8));
    }

    public String create(Long appointmentId, Action action, Instant expiresAt) {
        String payload = appointmentId + ":" + action.name() + ":" + expiresAt.getEpochSecond();
        String encodedPayload = b64(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + b64(hmac(key, encodedPayload.getBytes(StandardCharsets.UTF_8)));
    }

    /** Retorna vazio se o token for inválido, adulterado ou expirado. */
    public Optional<ParsedToken> parse(String token) {
        if (token == null) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        String encodedPayload = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        try {
            byte[] expected = hmac(key, encodedPayload.getBytes(StandardCharsets.UTF_8));
            byte[] given = Base64.getUrlDecoder().decode(signature);
            if (!MessageDigest.isEqual(expected, given)) return Optional.empty();

            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            if (parts.length != 3) return Optional.empty();
            if (Instant.now().getEpochSecond() > Long.parseLong(parts[2])) return Optional.empty();
            return Optional.of(new ParsedToken(Long.parseLong(parts[0]), Action.valueOf(parts[1])));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC indisponível", ex);
        }
    }
}
