package br.com.clube_quinze.api.service.google;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Parâmetro "state" do OAuth: assinado (HMAC) e com validade curta. Amarra o retorno do Google ao administrador
 * que iniciou a conexão e impede que alguém use um código de autorização forjado no nosso endereço de retorno.
 */
@Service
public class GoogleOAuthStateService {

    private static final long VALID_SECONDS = 10 * 60L;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public GoogleOAuthStateService(@Value("${app.security.jwt.secret}") String secret) {
        this.key = hmac(secret.getBytes(StandardCharsets.UTF_8), "google-oauth-state-v1".getBytes(StandardCharsets.UTF_8));
    }

    public String create(Long adminUserId) {
        return create(adminUserId, VALID_SECONDS);
    }

    /** Visível ao pacote para os testes poderem criar um state já vencido. */
    String create(Long adminUserId, long validSeconds) {
        byte[] nonce = new byte[9];
        random.nextBytes(nonce);
        String payload = adminUserId + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                + ":" + (Instant.now().getEpochSecond() + validSeconds);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, encoded.getBytes(StandardCharsets.UTF_8)));
    }

    /** Devolve o id do administrador se o state for válido e não tiver expirado. */
    public Optional<Long> parse(String state) {
        if (state == null) return Optional.empty();
        int dot = state.indexOf('.');
        if (dot <= 0 || dot == state.length() - 1) return Optional.empty();
        String encoded = state.substring(0, dot);
        try {
            byte[] expected = hmac(key, encoded.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(state.substring(dot + 1)))) {
                return Optional.empty();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split(":");
            if (parts.length != 3 || Instant.now().getEpochSecond() > Long.parseLong(parts[2])) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(parts[0]));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
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
