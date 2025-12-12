package br.com.clube_quinze.api.dto.community;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostRequest(
        @NotBlank(message = "Título é obrigatório")
        String title,
        @NotBlank(message = "Conteúdo é obrigatório")
        String content,
        @Size(max = 6, message = "Limite máximo de 6 fotos por post")
        List<@Valid PostMediaRequest> media) {
}
