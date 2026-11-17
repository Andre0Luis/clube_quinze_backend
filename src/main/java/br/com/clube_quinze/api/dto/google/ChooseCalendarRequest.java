package br.com.clube_quinze.api.dto.google;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Escolha da agenda de destino. mode = CREATE cria uma agenda nova (name opcional; padrão "Clube Quinze");
 * mode = EXISTING usa uma agenda que já existe na conta (calendarId obrigatório).
 */
public record ChooseCalendarRequest(
        @NotBlank @Pattern(regexp = "CREATE|EXISTING", message = "mode deve ser CREATE ou EXISTING") String mode,
        String calendarId,
        @Size(max = 100) String name) {
}
