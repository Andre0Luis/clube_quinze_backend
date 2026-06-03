package br.com.clube_quinze.api.dto.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para envio de push notification em broadcast para todos os membros.
 * Suporta emojis — o FCM aceita UTF-8 completo.
 */
public record BroadcastRequest(

        @NotBlank(message = "Título é obrigatório")
        @Size(max = 100, message = "Título deve ter no máximo 100 caracteres")
        String title,

        @NotBlank(message = "Mensagem é obrigatória")
        @Size(max = 500, message = "Mensagem deve ter no máximo 500 caracteres")
        String body
) {}
