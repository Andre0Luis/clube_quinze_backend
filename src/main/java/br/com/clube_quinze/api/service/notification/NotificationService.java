package br.com.clube_quinze.api.service.notification;

public interface NotificationService {
    void notifyFeedbackReceived(Long userId, Long appointmentId, Integer rating);

    void notifyWelcome(String email, String name, String rawPassword);
}
