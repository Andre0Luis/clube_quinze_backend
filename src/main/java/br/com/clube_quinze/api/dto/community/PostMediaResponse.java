package br.com.clube_quinze.api.dto.community;

import java.io.Serializable;

public record PostMediaResponse(
        Long id,
        Integer position,
        String imageUrl,
        String imageBase64) implements Serializable {
}
