package br.com.clube_quinze.api.dto.settings;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Configuração dos lembretes que o admin recebe antes de cada atendimento.
 * offsets = minutos antes do agendamento (ex: [60, 30] = 1h e 30min antes).
 */
public record AdminNotificationSettings(
        @NotNull Boolean enabled,
        @NotNull List<Integer> offsets
) {}
