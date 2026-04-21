package br.com.clube_quinze.api.dto.notifications;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

public record PushTokenRequest(@NotBlank String token, String platform) implements Serializable {
}
