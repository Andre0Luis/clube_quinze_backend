package br.com.clube_quinze.api.dto.payment;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.PaymentStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRequest(
        @NotNull(message = "Usuário é obrigatório")
        Long userId,

        @NotNull(message = "Valor é obrigatório")
        @Min(value = 0, message = "Valor deve ser positivo")
        BigDecimal amount,

        PaymentStatus status,

        String paymentMethod,

        LocalDateTime paidAt,

        String description) {
}
