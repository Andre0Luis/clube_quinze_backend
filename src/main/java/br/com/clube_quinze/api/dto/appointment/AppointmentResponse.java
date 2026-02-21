package br.com.clube_quinze.api.dto.appointment;

import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        Long clientId,
        LocalDateTime scheduledAt,
        MembershipTier appointmentTier,
        AppointmentStatus status,
        String serviceType,
        String notes,
        Integer durationMinutes) {
}
