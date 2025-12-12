package br.com.clube_quinze.api.dto.community;

public record PostMediaResponse(
        Long id,
        Integer position,
        String imageUrl,
        String imageBase64) {
}
