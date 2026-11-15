package br.com.clube_quinze.api.dto.google;

import java.io.Serializable;

/** Agenda da conta Google que o administrador pode escolher como destino dos agendamentos. */
public record GoogleCalendarOption(String id, String summary, boolean primary) implements Serializable {
}
