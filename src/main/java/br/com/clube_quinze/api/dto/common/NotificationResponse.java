package br.com.clube_quinze.api.dto.common;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        String title,
        String message,
        NotificationType type,
        boolean read,
        LocalDateTime sentAt) implements Serializable {
}
