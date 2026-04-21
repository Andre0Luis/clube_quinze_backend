package br.com.clube_quinze.api.dto.common;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(
        @NotNull(message = "Usuário é obrigatório")
        Long userId,
        @NotBlank(message = "Título é obrigatório")
        String title,
        @NotBlank(message = "Mensagem é obrigatória")
        String message,
        @NotNull(message = "Tipo é obrigatório")
        NotificationType type) {
}
