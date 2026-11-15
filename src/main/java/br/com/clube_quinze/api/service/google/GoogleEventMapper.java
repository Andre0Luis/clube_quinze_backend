package br.com.clube_quinze.api.service.google;

import br.com.clube_quinze.api.config.AppConfig;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Monta o evento do Google a partir do agendamento: título, descrição e cor por status (confirmado = verde). */
@Component
public class GoogleEventMapper {

    private static final DateTimeFormatter PT_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private final String clubLocation;

    public GoogleEventMapper(@Value("${app.club.location:}") String clubLocation) {
        this.clubLocation = clubLocation == null ? "" : clubLocation;
    }

    /** Cores do Google Agenda: 10 verde, 5 amarelo, 6 laranja, 8 grafite. */
    static String colorFor(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED -> "10";
            case RESCHEDULE_REQUESTED -> "6";
            case COMPLETED -> "8";
            default -> "5";
        };
    }

    static String statusLabel(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED -> "Confirmado";
            case RESCHEDULE_REQUESTED -> "Remarcação pendente de aprovação";
            case COMPLETED -> "Concluído";
            case CANCELED -> "Cancelado";
            default -> "Agendado (aguardando confirmação)";
        };
    }

    public GoogleEvent toEvent(Appointment a) {
        String client = a.getClient() != null && a.getClient().getName() != null ? a.getClient().getName() : "Cliente";
        String service = a.getServiceType() != null && !a.getServiceType().isBlank() ? a.getServiceType().trim() : "Atendimento";
        String prefix = a.getStatus() == AppointmentStatus.RESCHEDULE_REQUESTED ? "Remarcação pendente — " : "";

        StringBuilder description = new StringBuilder();
        description.append("Status: ").append(statusLabel(a.getStatus())).append('\n');
        description.append("Cliente: ").append(client).append('\n');
        if (a.getStatus() == AppointmentStatus.RESCHEDULE_REQUESTED && a.getProposedAt() != null) {
            description.append("Novo horário proposto: ").append(a.getProposedAt().format(PT_BR)).append('\n');
        }
        if (a.getNotes() != null && !a.getNotes().isBlank()) {
            description.append('\n').append(a.getNotes().trim());
        }

        return new GoogleEvent(
                prefix + service + " — " + client,
                description.toString().trim(),
                clubLocation,
                a.getScheduledAt(),
                a.getScheduledAt().plusMinutes(a.getDurationMinutes()),
                AppConfig.APP_ZONE.getId(),
                colorFor(a.getStatus()));
    }
}
