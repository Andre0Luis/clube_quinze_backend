package br.com.clube_quinze.api.dto.auth;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record RegisterRequest(
        @NotBlank(message = "Nome é obrigatório")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve conter pelo menos 8 caracteres")
        String password,

        String phone,

        LocalDate birthDate,

        @NotNull(message = "Tipo de membro é obrigatório")
        MembershipTier membershipTier,

        LocalTime preferredAppointmentTime,
        Long planId) {
}
