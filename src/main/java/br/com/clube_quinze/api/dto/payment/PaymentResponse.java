package br.com.clube_quinze.api.dto.payment;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long userId,
        BigDecimal amount,
        PaymentStatus status,
        String paymentMethod,
        LocalDateTime paidAt,
        String description) implements Serializable {
}
