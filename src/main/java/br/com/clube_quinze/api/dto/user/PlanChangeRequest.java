package br.com.clube_quinze.api.dto.user;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import jakarta.validation.constraints.NotNull;

public record PlanChangeRequest(
        @NotNull(message = "Tipo de membro é obrigatório")
        MembershipTier membershipTier,
        Integer durationMonths) {
}