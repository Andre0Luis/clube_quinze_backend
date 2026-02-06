package br.com.clube_quinze.api.service.notification;

import java.util.Map;

public interface PushNotificationService {
    void sendToUser(Long userId, String kind, String title, String body, Map<String, Object> data);
}
