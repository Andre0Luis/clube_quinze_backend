package br.com.clube_quinze.api.dto.admin;

import java.io.Serializable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PlanRequest(
        @NotBlank(message = "Nome é obrigatório")
        String name,
        String description,
        @NotNull(message = "Preço é obrigatório")
        @Min(value = 0, message = "Preço deve ser positivo")
        BigDecimal price,
        @Min(value = 1, message = "Duração mínima é 1 mês")
        int durationMonths) {
}
