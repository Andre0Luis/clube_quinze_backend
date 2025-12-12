package br.com.clube_quinze.api.dto.user;

import br.com.clube_quinze.api.dto.appointment.AppointmentResponse;
import br.com.clube_quinze.api.dto.payment.PlanSummary;
import br.com.clube_quinze.api.dto.preference.PreferenceResponse;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        MembershipTier membershipTier,
        RoleType role,
        PlanSummary plan,
        LocalDateTime createdAt,
        LocalDateTime lastLogin,
        AppointmentResponse nextAppointment,
        List<PreferenceResponse> preferences,
        String profilePictureUrl,
        String profilePictureBase64,
        List<UserGalleryPhotoResponse> gallery) {
}
