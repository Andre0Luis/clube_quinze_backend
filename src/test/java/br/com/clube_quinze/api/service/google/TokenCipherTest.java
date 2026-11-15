package br.com.clube_quinze.api.service.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Criptografia dos tokens do Google (AES-GCM)")
class TokenCipherTest {

    private static String key(int bytes) {
        byte[] k = new byte[bytes];
        for (int i = 0; i < k.length; i++) k[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(k);
    }

    private static TokenCipher cipher(String key) {
        GoogleProperties properties = new GoogleProperties();
        properties.setTokenEncryptionKey(key);
        return new TokenCipher(properties);
    }

    @Test
    @DisplayName("ida e volta devolve o original, e o valor guardado não contém o texto puro")
    void idaEVolta() {
        TokenCipher c = cipher(key(32));
        String stored = c.encrypt("1//refresh-token-secreto");

        assertThat(stored).doesNotContain("refresh-token-secreto");
        assertThat(c.decrypt(stored)).isEqualTo("1//refresh-token-secreto");
    }

    @Test
    @DisplayName("o mesmo texto criptografado duas vezes gera valores diferentes (IV aleatório)")
    void ivAleatorio() {
        TokenCipher c = cipher(key(32));

        assertThat(c.encrypt("igual")).isNotEqualTo(c.encrypt("igual"));
    }

    @Test
    @DisplayName("valor adulterado ou chave diferente não decifra")
    void adulteracaoEOutraChave() {
        TokenCipher c = cipher(key(32));
        String stored = c.encrypt("segredo");
        byte[] raw = Base64.getDecoder().decode(stored);
        raw[raw.length - 1] ^= 1;

        assertThatThrownBy(() -> c.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[32])).decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sem chave ou com chave de tamanho errado, falha com mensagem clara")
    void chaveInvalida() {
        assertThatThrownBy(() -> cipher("").encrypt("x")).hasMessageContaining("GOOGLE_TOKEN_ENCRYPTION_KEY");
        assertThatThrownBy(() -> cipher(key(16)).encrypt("x")).hasMessageContaining("32 bytes");
    }
}
