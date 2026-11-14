package br.com.clube_quinze.api.dto.appointment;

import jakarta.validation.constraints.Size;

public record AppointmentRescheduleRejectRequest(
        @Size(max = 300, message = "O motivo deve ter no máximo 300 caracteres")
        String reason) {
}
