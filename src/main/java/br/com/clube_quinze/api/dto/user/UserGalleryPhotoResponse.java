package br.com.clube_quinze.api.dto.user;

import java.io.Serializable;

public record UserGalleryPhotoResponse(
        Long id,
        Integer position,
        String imageUrl,
        String imageBase64) implements Serializable {
}
