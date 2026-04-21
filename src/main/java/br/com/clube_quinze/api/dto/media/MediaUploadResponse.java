package br.com.clube_quinze.api.dto.media;

import java.io.Serializable;

public record MediaUploadResponse(
        String url,
        String path,
        long size,
        String contentType
) implements Serializable {
}
