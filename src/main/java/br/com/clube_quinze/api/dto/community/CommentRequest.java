package br.com.clube_quinze.api.dto.community;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(@NotBlank(message = "Conteúdo é obrigatório") String content) {
}
