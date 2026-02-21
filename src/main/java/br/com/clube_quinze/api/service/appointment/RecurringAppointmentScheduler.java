package br.com.clube_quinze.api.service.appointment;

import br.com.clube_quinze.api.dto.appointment.AppointmentRequest;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.service.appointment.AppointmentService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecurringAppointmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringAppointmentScheduler.class);

    private final AppointmentService appointmentService;
    private final Clock clock;

    public RecurringAppointmentScheduler(AppointmentService appointmentService, Clock clock) {
        this.appointmentService = appointmentService;
        this.clock = clock;
    }

    public void scheduleForNewUser(User user, MembershipTier tier, LocalTime preferredTime, int recurrenceMonths) {
        if (user == null || tier == null || recurrenceMonths <= 0) {
            return;
        }

        LocalTime slotTime = alignTime(preferredTime);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate candidateDate = now.toLocalDate();
        LocalDateTime firstSlot = candidateDate.atTime(slotTime);
        while (!firstSlot.isAfter(now)) {
            candidateDate = candidateDate.plusDays(1);
            firstSlot = candidateDate.atTime(slotTime);
        }
        LocalDateTime horizon = firstSlot.plusMonths(recurrenceMonths);
        Duration interval = frequencyForTier(tier);
        int durationMinutes = durationForTier(tier);

        LocalDateTime cursor = firstSlot;
        while (cursor.isBefore(horizon)) {
            AppointmentRequest request = new AppointmentRequest(
                    user.getId(),
                    cursor,
                    tier,
                    null,
                    "Agendamento recorrente automático",
                    durationMinutes);
            try {
                appointmentService.schedule(user.getId(), false, request);
            } catch (BusinessException ex) {
                log.warn("Não foi possível criar agendamento recorrente para o usuário {} no slot {}: {}",
                        user.getId(), cursor, ex.getMessage());
            }
            cursor = cursor.plus(interval);
        }
    }

    private Duration frequencyForTier(MembershipTier tier) {
        return tier == MembershipTier.QUINZE_PREMIUM ? Duration.ofDays(7) : Duration.ofDays(14);
    }

    private int durationForTier(MembershipTier tier) {
        return tier == MembershipTier.QUINZE_SELECT ? 120 : 60;
    }

    private LocalTime alignTime(LocalTime preferredTime) {
        LocalTime candidate = preferredTime != null ? preferredTime : AppointmentScheduleSettings.DEFAULT_RECOMMENDED_TIME;
        LocalTime opening = AppointmentScheduleSettings.OPENING_TIME;
        LocalTime lastStart = AppointmentScheduleSettings.CLOSING_TIME.minus(AppointmentScheduleSettings.SLOT_DURATION);
        if (candidate.isBefore(opening)) {
            candidate = opening;
        } else if (candidate.isAfter(lastStart)) {
            candidate = lastStart;
        }
        long slotMinutes = AppointmentScheduleSettings.SLOT_DURATION.toMinutes();
        long totalMinutes = candidate.getHour() * 60L + candidate.getMinute();
        long remainder = totalMinutes % slotMinutes;
        if (remainder != 0) {
            totalMinutes -= remainder;
        }
        int hour = (int) (totalMinutes / 60);
        int minute = (int) (totalMinutes % 60);
        return LocalTime.of(hour, minute);
    }
}
