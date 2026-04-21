package br.com.clube_quinze.api.dto.appointment;

import java.io.Serializable;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AvailableSlotResponse(
        LocalDate date,
        MembershipTier membershipTier,
        List<LocalDateTime> availableSlots) implements Serializable {
}
