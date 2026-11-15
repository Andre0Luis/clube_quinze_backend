package br.com.clube_quinze.api.service.google;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Criptografia AES-256-GCM dos tokens do Google guardados no banco (IV aleatório por valor; GCM detecta adulteração). */
@Component
public class TokenCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final GoogleProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(GoogleProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao criptografar o token", ex);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Token criptografado inválido ou chave de criptografia diferente", ex);
        }
    }

    private SecretKeySpec key() {
        String configured = properties.getTokenEncryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("GOOGLE_TOKEN_ENCRYPTION_KEY não está definida");
        }
        byte[] key = Base64.getDecoder().decode(configured.trim());
        if (key.length != 32) {
            throw new IllegalStateException("GOOGLE_TOKEN_ENCRYPTION_KEY deve ter 32 bytes (base64)");
        }
        return new SecretKeySpec(key, "AES");
    }
}
