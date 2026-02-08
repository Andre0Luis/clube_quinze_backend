package br.com.clube_quinze.api.dto.user;

import br.com.clube_quinze.api.dto.payment.PlanSummary;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserSummary(
        Long id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        MembershipTier membershipTier,
        RoleType role,
        LocalDateTime createdAt,
        LocalDateTime lastLogin,
        PlanSummary plan) {
}
