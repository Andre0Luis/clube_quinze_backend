package br.com.clube_quinze.api.service.notification;

public interface NotificationService {
    void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating);

    void notifyWelcome(String email, String name, String rawPassword);

    void notifyPasswordReset(String email, String name, String resetLink);

    void notifyAppointmentReminder(String email, String name, String scheduledAt, String description, String offsetLabel);

    void notifyAppointmentRescheduled(String email, String name, String oldScheduledAt, String newScheduledAt, String description);
}
