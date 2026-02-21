package br.com.clube_quinze.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email e obrigatorio")
        @Email(message = "Email invalido")
        String email) {
}
