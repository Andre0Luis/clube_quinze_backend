package br.com.clube_quinze.api.service.google;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("State assinado do OAuth do Google")
class GoogleOAuthStateServiceTest {

    private final GoogleOAuthStateService service = new GoogleOAuthStateService("segredo-de-teste-123456");

    @Test
    @DisplayName("state válido devolve o administrador que iniciou a conexão")
    void estadoValido() {
        assertThat(service.parse(service.create(42L))).contains(42L);
    }

    @Test
    @DisplayName("state vencido, adulterado, de outro segredo ou lixo é recusado")
    void estadoInvalido() {
        String valid = service.create(42L);

        assertThat(service.parse(service.create(42L, -5))).isEmpty();
        assertThat(service.parse(valid.substring(0, valid.length() - 2) + "xx")).isEmpty();
        assertThat(new GoogleOAuthStateService("outro-segredo-999999").parse(valid)).isEmpty();
        assertThat(service.parse(null)).isEmpty();
        assertThat(service.parse("lixo")).isEmpty();
        assertThat(service.parse("a.b")).isEmpty();
    }

    @Test
    @DisplayName("cada state é diferente (nonce)")
    void estadosDiferentes() {
        assertThat(service.create(1L)).isNotEqualTo(service.create(1L));
    }
}
