package br.com.clube_quinze.api.integration.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.integration.AbstractIntegrationTest;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Agendamentos — links de calendário (.ics e Google Agenda)")
@SuppressWarnings({"rawtypes", "unchecked"})
class AppointmentCalendarIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/appointments";

    @Autowired
    private AppointmentActionTokenService tokenService;

    private String memberToken;
    private Long appointmentId;

    private String register() {
        String email = "cal_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        ResponseEntity<Map> res = postPublic("/api/v1/auth/register",
                Map.of("name", "Membro Calendário", "email", email, "password", "Senha@1234",
                        "membershipTier", "QUINZE_STANDARD"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) res.getBody().get("accessToken");
    }

    @BeforeEach
    void setup() {
        memberToken = register();
        Long memberId = ((Number) get("/api/v1/users/me", memberToken, Map.class).getBody().get("id")).longValue();
        LocalDate day = LocalDate.now().plusDays(420 + (long) (Math.random() * 60));
        ResponseEntity<Map> created = post(BASE, Map.of(
                "clientId", memberId,
                "scheduledAt", day.atTime(9 + (int) (Math.random() * 10), 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "appointmentTier", "QUINZE_STANDARD",
                "serviceType", "Corte Calendário"), memberToken, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        appointmentId = ((Number) created.getBody().get("id")).longValue();
    }

    @Test
    @DisplayName("GET /{id}/calendar.ics → dono e admin baixam o .ics; outro cliente e anônimo não")
    void baixaIcsComPermissao() {
        ResponseEntity<String> owner = get(BASE + "/" + appointmentId + "/calendar.ics", memberToken, String.class);

        assertThat(owner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.getHeaders().getContentType().toString()).startsWith("text/calendar");
        assertThat(owner.getHeaders().getFirst("Content-Disposition")).contains("agendamento-" + appointmentId + ".ics");
        assertThat(owner.getBody()).contains("BEGIN:VEVENT", "SUMMARY:Clube Quinze — Corte Calendário");

        assertThat(get(BASE + "/" + appointmentId + "/calendar.ics", adminToken(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get(BASE + "/" + appointmentId + "/calendar.ics", register(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url(BASE + "/" + appointmentId + "/calendar.ics"), String.class)
                .getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    @DisplayName("GET /{id}/calendar-links → devolve o link do Google Agenda e o link público do .ics, que abre sem login")
    void linksFuncionam() {
        ResponseEntity<Map> links = get(BASE + "/" + appointmentId + "/calendar-links", memberToken, Map.class);

        assertThat(links.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) links.getBody().get("googleCalendarUrl"))
                .startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .contains("dates=");

        String icsUrl = (String) links.getBody().get("icsUrl");
        String token = icsUrl.substring(icsUrl.indexOf("token=") + 6);
        ResponseEntity<String> publicIcs = restTemplate.getForEntity(url("/appointment-calendar?token=" + token), String.class);
        assertThat(publicIcs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicIcs.getBody()).contains("BEGIN:VCALENDAR", "UID:appointment-" + appointmentId + "@");
    }

    @Test
    @DisplayName("GET /appointment-calendar → token inválido, expirado ou de outra ação recebe 404")
    void tokenInvalidoRecebe404() {
        String confirmToken = tokenService.create(appointmentId, Action.CONFIRM, Instant.now().plusSeconds(600));
        String expired = tokenService.create(appointmentId, Action.CALENDAR, Instant.now().minusSeconds(5));

        for (String bad : new String[] {"lixo", confirmToken, expired}) {
            ResponseEntity<String> response = restTemplate.getForEntity(url("/appointment-calendar?token=" + bad), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
