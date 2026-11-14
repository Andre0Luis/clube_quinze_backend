package br.com.clube_quinze.api.service.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Convites de calendário (.ics e Google Agenda)")
class AppointmentCalendarServiceTest {

    private final AppointmentActionTokenService tokens = new AppointmentActionTokenService("segredo-de-teste-123456");
    private final AppointmentCalendarService service = new AppointmentCalendarService(
            null, tokens, "https://clubequinzeapp.cloud/", "Clube Quinze", "Rua das Flores, 15 - Centro");

    private Appointment appointment() {
        Appointment a = new Appointment();
        a.setId(7L);
        a.setScheduledAt(LocalDateTime.of(2027, 3, 10, 10, 0)); // relógio de Brasília (UTC-3)
        a.setDurationMinutes(45);
        a.setServiceType("Corte, barba; premium");
        a.setNotes("Linha 1\nLinha 2");
        a.setStatus(AppointmentStatus.CONFIRMED);
        return a;
    }

    @Test
    @DisplayName(".ics converte Brasília para UTC, escapa texto e usa CRLF")
    void icsComHorarioEmUtc() {
        String ics = service.buildIcs(appointment());

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("DTSTART:20270310T130000Z", "DTEND:20270310T134500Z");
        assertThat(ics).contains("UID:appointment-7@clubequinzeapp.cloud");
        assertThat(ics).contains("SUMMARY:Clube Quinze — Corte\\, barba\\; premium");
        assertThat(ics).contains("DESCRIPTION:Linha 1\\nLinha 2\\n\\nAgendamento no Clube Quinze");
        assertThat(ics).contains("LOCATION:Rua das Flores\\, 15 - Centro");
        assertThat(ics).contains("STATUS:CONFIRMED");
        assertThat(ics).contains("TRIGGER:-PT60M");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    @DisplayName("agendamento cancelado sai com STATUS:CANCELLED")
    void icsCancelado() {
        Appointment a = appointment();
        a.setStatus(AppointmentStatus.CANCELED);

        assertThat(service.buildIcs(a)).contains("STATUS:CANCELLED");
    }

    @Test
    @DisplayName("linhas longas são dobradas em 75 bytes sem partir caracteres UTF-8 e desdobram de volta ao original")
    void dobraLinhasLongas() {
        Appointment a = appointment();
        a.setNotes("Atenção à observação importante: ".repeat(12) + "fim — ✂");

        String ics = service.buildIcs(a);

        for (String physical : ics.split("\r\n")) {
            assertThat(physical.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("fim — ✂");
        assertThat(unfolded).doesNotContain("�");
    }

    @Test
    @DisplayName("link do Google Agenda leva título, datas em UTC e local")
    void linkDoGoogle() {
        String url = service.googleCalendarUrl(appointment());

        assertThat(url).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE");
        assertThat(url).contains("dates=20270310T130000Z/20270310T134500Z");
        String text = URLDecoder.decode(url.split("text=")[1].split("&")[0], StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("Clube Quinze — Corte, barba; premium");
        String location = URLDecoder.decode(url.split("location=")[1].split("&")[0], StandardCharsets.UTF_8);
        assertThat(location).isEqualTo("Rua das Flores, 15 - Centro");
    }

    @Test
    @DisplayName("link do .ics é assinado com a ação CALENDAR")
    void linkDoIcsAssinado() {
        String url = service.icsUrl(appointment());

        assertThat(url).startsWith("https://clubequinzeapp.cloud/appointment-calendar?token=");
        var parsed = tokens.parse(url.substring(url.indexOf("token=") + 6));
        assertThat(parsed).isPresent();
        assertThat(parsed.get().appointmentId()).isEqualTo(7L);
        assertThat(parsed.get().action()).isEqualTo(Action.CALENDAR);
    }

    @Test
    @DisplayName("sem serviço informado o título é genérico e sem local o evento não leva LOCATION")
    void tituloGenericoESemLocal() {
        AppointmentCalendarService noLocation = new AppointmentCalendarService(null, tokens,
                "https://clubequinzeapp.cloud", "Clube Quinze", "");
        Appointment a = appointment();
        a.setServiceType(null);

        String ics = noLocation.buildIcs(a);

        assertThat(ics).contains("SUMMARY:Atendimento — Clube Quinze");
        assertThat(ics).doesNotContain("LOCATION:");
        assertThat(noLocation.googleCalendarUrl(a)).doesNotContain("location=");
    }
}
