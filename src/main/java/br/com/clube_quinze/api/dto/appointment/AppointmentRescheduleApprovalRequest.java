package br.com.clube_quinze.api.dto.appointment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

/**
 * Aprovação de uma proposta de remarcação. Ambos os campos são opcionais:
 * newDate = aprovar em um horário diferente do proposto (útil quando há conflito);
 * durationMinutes = admin define a duração (padrão: a do agendamento atual).
 */
public record AppointmentRescheduleApprovalRequest(
        LocalDateTime newDate,
        @Min(value = 15, message = "Duração mínima é de 15 minutos")
        @Max(value = 480, message = "Duração máxima é de 8 horas")
        Integer durationMinutes) {
}
