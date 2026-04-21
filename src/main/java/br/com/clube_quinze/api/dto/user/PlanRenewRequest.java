package br.com.clube_quinze.api.dto.user;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

public record PlanRenewRequest(
        @NotNull(message = "Duração é obrigatória")
        Integer durationMonths) {
}