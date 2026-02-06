package br.com.clube_quinze.api.service.notification;

import java.util.List;
import java.util.Map;

public interface ExpoPushService {
    record ExpoMessage(String to, String title, String body, Map<String, Object> data) {}
    record ExpoResult(boolean ok, String status, String message) {}

    List<ExpoResult> sendBatch(List<ExpoMessage> messages);
}
