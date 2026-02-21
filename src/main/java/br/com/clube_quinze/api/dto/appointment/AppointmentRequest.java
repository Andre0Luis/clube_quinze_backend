package br.com.clube_quinze.api.dto.appointment;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AppointmentRequest(
        @NotNull(message = "Cliente é obrigatório")
        Long clientId,

        @NotNull(message = "Data/Hora é obrigatória")
        @Future(message = "Agendamento deve ser no futuro")
        LocalDateTime scheduledAt,

        @NotNull(message = "Tipo de atendimento é obrigatório")
        MembershipTier appointmentTier,

        String serviceType,

        String notes,
        Integer durationMinutes) {
}
