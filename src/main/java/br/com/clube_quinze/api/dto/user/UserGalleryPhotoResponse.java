package br.com.clube_quinze.api.dto.user;

public record UserGalleryPhotoResponse(
        Long id,
        Integer position,
        String imageUrl,
        String imageBase64) {
}
