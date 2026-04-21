package br.com.clube_quinze.api.dto.appointment;

import java.io.Serializable;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AppointmentRescheduleRequest(
        @NotNull(message = "Nova data é obrigatória")
        @Future(message = "Agendamento deve ser no futuro")
        LocalDateTime newDate,
        String notes) {
}
