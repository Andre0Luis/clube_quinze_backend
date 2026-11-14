package br.com.clube_quinze.api.dto.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Parâmetros do fluxo de confirmação de agendamentos (editáveis pelo admin / futuro painel de personalização).
 * Todos os prazos são em minutos ANTES do horário do atendimento.
 *
 * @param enabled                       liga/desliga o pedido de confirmação e o alerta ao admin
 * @param confirmationRequestOffset     quanto antes pedir a confirmação ao cliente (padrão 90 = 1h30)
 * @param adminAlertOffset              quanto antes avisar o admin se o cliente não confirmou (padrão 60 = 1h);
 *                                      precisa ser menor que o pedido de confirmação
 * @param clientReminderOffsets         momentos dos lembretes simples ao cliente (padrão 24h, 3h, 1h, 30min)
 * @param skipRemindersWhenConfirmed    lembretes (entre os acima) que NÃO são enviados se o cliente já confirmou
 *                                      (padrão: o de 1h)
 */
public record AppointmentConfirmationSettings(
        @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(10080) Integer confirmationRequestOffset,
        @NotNull @Min(1) @Max(10080) Integer adminAlertOffset,
        @NotNull List<Integer> clientReminderOffsets,
        @NotNull List<Integer> skipRemindersWhenConfirmed
) {}
