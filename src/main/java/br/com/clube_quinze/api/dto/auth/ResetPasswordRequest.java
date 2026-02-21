package br.com.clube_quinze.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token e obrigatorio")
        String token,

        @NotBlank(message = "Senha e obrigatoria")
        @Size(min = 8, message = "Senha deve conter pelo menos 8 caracteres")
        String newPassword) {
}
