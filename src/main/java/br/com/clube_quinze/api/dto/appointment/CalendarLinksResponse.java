package br.com.clube_quinze.api.dto.appointment;

import java.io.Serializable;

/** Links para salvar o agendamento na agenda: Google Agenda e arquivo .ics (Apple, Outlook etc.). */
public record CalendarLinksResponse(String googleCalendarUrl, String icsUrl) implements Serializable {
}
