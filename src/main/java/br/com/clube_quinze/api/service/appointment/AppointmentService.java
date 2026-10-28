package br.com.clube_quinze.api.service.appointment;

import br.com.clube_quinze.api.dto.appointment.AppointmentRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentRescheduleRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentResponse;
import br.com.clube_quinze.api.dto.appointment.AppointmentStatusUpdateRequest;
import br.com.clube_quinze.api.dto.appointment.AvailableSlotResponse;
import br.com.clube_quinze.api.dto.common.PageResponse;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import java.time.LocalDate;

public interface AppointmentService {

    /**
     * @param durationMinutes se informado (uso do admin), lista inícios em grade de 15 min onde cabe essa
     *                        duração; se nulo, mantém a grade padrão do plano (Select 2h / demais 30 min).
     */
    AvailableSlotResponse getAvailableSlots(LocalDate date, MembershipTier tier, Integer durationMinutes);

    AppointmentResponse schedule(Long actorId, boolean privileged, AppointmentRequest request);

    AppointmentResponse reschedule(Long appointmentId, Long actorId, boolean privileged, AppointmentRescheduleRequest request);

    AppointmentResponse updateStatus(Long appointmentId, AppointmentStatusUpdateRequest request);

    void cancel(Long appointmentId, Long actorId, boolean privileged);

    AppointmentResponse getAppointment(Long appointmentId, Long actorId, boolean privileged);

    PageResponse<AppointmentResponse> getAppointmentsForUser(
            Long userId,
            AppointmentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size);

    PageResponse<AppointmentResponse> getAppointments(
            AppointmentStatus status,
            Long clientId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size);
}
