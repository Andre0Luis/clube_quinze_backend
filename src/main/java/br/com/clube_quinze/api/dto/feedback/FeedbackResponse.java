package br.com.clube_quinze.api.dto.feedback;

import java.io.Serializable;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long id,
        Long appointmentId,
        Long userId,
        int rating,
        String comment,
        LocalDateTime createdAt) implements Serializable {
}
