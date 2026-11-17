package br.com.clube_quinze.api.service.notification;

public interface NotificationService {
    void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating);

    void notifyWelcome(String email, String name, String rawPassword);

    void notifyPasswordReset(String email, String name, String resetLink);

    void notifyAppointmentReminder(String email, String name, String scheduledAt, String description, String offsetLabel,
                                   String googleCalendarUrl, String icsUrl);

    void notifyAppointmentRescheduled(String email, String name, String oldScheduledAt, String newScheduledAt, String description,
                                      String googleCalendarUrl, String icsUrl);

    /** E-mail do pedido de confirmação (botões Confirmar / Cancelar / Remarcar apontam para links assinados). */
    void notifyAppointmentConfirmationRequest(String email, String name, String scheduledAt, String description,
                                              String confirmUrl, String cancelUrl, String rescheduleUrl,
                                              String googleCalendarUrl, String icsUrl);
}
