package br.com.clube_quinze.api.dto.community;

import java.io.Serializable;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Serializable {
}
