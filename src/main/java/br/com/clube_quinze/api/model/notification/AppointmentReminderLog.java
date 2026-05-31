package br.com.clube_quinze.api.model.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Marca que um lembrete de um agendamento, para um determinado offset (24h/3h/1h/30min),
 * já foi enfileirado — garantindo idempotência (ver V13).
 */
@Entity
@Table(name = "lembrete_agendamento_log",
        uniqueConstraints = @UniqueConstraint(name = "uq_lembrete_appt_offset",
                columnNames = {"appointment_id", "offset_minutes"}))
public class AppointmentReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "offset_minutes", nullable = false)
    private int offsetMinutes;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public AppointmentReminderLog() {
    }

    public AppointmentReminderLog(Long appointmentId, int offsetMinutes) {
        this.appointmentId = appointmentId;
        this.offsetMinutes = offsetMinutes;
    }

    @PrePersist
    void prePersist() {
        if (this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public int getOffsetMinutes() {
        return offsetMinutes;
    }

    public void setOffsetMinutes(int offsetMinutes) {
        this.offsetMinutes = offsetMinutes;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
