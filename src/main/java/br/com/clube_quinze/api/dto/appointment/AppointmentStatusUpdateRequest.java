package br.com.clube_quinze.api.dto.appointment;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record AppointmentStatusUpdateRequest(
        @NotNull(message = "Status é obrigatório")
        AppointmentStatus status,
        String notes) {
}
