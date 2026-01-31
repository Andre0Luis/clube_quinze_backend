package br.com.clube_quinze.api.dto.media;

public record MediaUploadResponse(
        String url,
        String path,
        long size,
        String contentType
) {
}
