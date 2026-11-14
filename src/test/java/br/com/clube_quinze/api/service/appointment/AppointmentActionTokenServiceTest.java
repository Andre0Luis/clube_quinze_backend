package br.com.clube_quinze.api.service.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Token assinado dos botões do e-mail")
class AppointmentActionTokenServiceTest {

    private final AppointmentActionTokenService service = new AppointmentActionTokenService("segredo-de-teste-123456");

    @Test
    @DisplayName("token válido devolve agendamento e ação")
    void tokenValido() {
        String token = service.create(42L, Action.CONFIRM, Instant.now().plusSeconds(600));

        var parsed = service.parse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().appointmentId()).isEqualTo(42L);
        assertThat(parsed.get().action()).isEqualTo(Action.CONFIRM);
    }

    @Test
    @DisplayName("token expirado é recusado")
    void tokenExpirado() {
        String token = service.create(42L, Action.CANCEL, Instant.now().minusSeconds(5));

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("token adulterado (outro agendamento ou ação) é recusado")
    void tokenAdulterado() {
        String token = service.create(42L, Action.CONFIRM, Instant.now().plusSeconds(600));
        String signature = token.substring(token.indexOf('.'));
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("43:CANCEL:" + Instant.now().plusSeconds(600).getEpochSecond()).getBytes());

        assertThat(service.parse(forgedPayload + signature)).isEmpty();
        assertThat(service.parse(token.substring(0, token.length() - 2) + "xx")).isEmpty();
    }

    @Test
    @DisplayName("token assinado com outro segredo é recusado; lixo não quebra")
    void outroSegredoELixo() {
        String token = new AppointmentActionTokenService("outro-segredo-9999").create(
                1L, Action.CONFIRM, Instant.now().plusSeconds(600));

        assertThat(service.parse(token)).isEmpty();
        assertThat(service.parse(null)).isEmpty();
        assertThat(service.parse("")).isEmpty();
        assertThat(service.parse("abc")).isEmpty();
        assertThat(service.parse("abc.def")).isEmpty();
        assertThat(service.parse("...")).isEmpty();
    }
}
