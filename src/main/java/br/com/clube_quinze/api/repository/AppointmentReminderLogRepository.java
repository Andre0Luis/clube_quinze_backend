package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.notification.AppointmentReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentReminderLogRepository extends JpaRepository<AppointmentReminderLog, Long> {

    boolean existsByAppointmentIdAndOffsetMinutes(Long appointmentId, int offsetMinutes);
}
