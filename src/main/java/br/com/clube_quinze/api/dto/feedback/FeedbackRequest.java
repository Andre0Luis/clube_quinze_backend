package br.com.clube_quinze.api.dto.feedback;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotNull(message = "Agendamento é obrigatório")
        Long appointmentId,
        @Min(value = 1, message = "Nota mínima é 1")
        @Max(value = 5, message = "Nota máxima é 5")
        int rating,
        String comment) {
}
