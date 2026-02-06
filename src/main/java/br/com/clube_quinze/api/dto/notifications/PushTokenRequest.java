package br.com.clube_quinze.api.dto.notifications;

import jakarta.validation.constraints.NotBlank;

public record PushTokenRequest(@NotBlank String token, String platform) {
}
