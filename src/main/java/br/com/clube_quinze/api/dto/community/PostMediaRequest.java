package br.com.clube_quinze.api.dto.community;

import jakarta.validation.constraints.PositiveOrZero;

public record PostMediaRequest(
        @PositiveOrZero(message = "Posição deve ser zero ou positiva")
        Integer position,
        String imageUrl,
        String imageBase64) {
}
