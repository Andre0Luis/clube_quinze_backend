package br.com.clube_quinze.api.dto.user;

import jakarta.validation.constraints.PositiveOrZero;

public record UserGalleryPhotoRequest(
        @PositiveOrZero(message = "Posição deve ser zero ou positiva")
        Integer position,
        String imageUrl,
        String imageBase64) {
}
