package br.com.clube_quinze.api.service.notification;

import java.util.List;
import java.util.Map;

public interface ExpoPushService {
    record ExpoMessage(String to, String title, String body, Map<String, Object> data) {}
    record ExpoResult(boolean ok, String status, String message) {}

    List<ExpoResult> sendBatch(List<ExpoMessage> messages);

    /**
     * Variante diagnóstica. Quando {@code dryRun} é true, o provedor valida credencial e token
     * sem entregar a notificação ao dispositivo (suportado pelo FCM). Implementações que não
     * suportam dry-run caem no envio normal.
     */
    default List<ExpoResult> sendBatch(List<ExpoMessage> messages, boolean dryRun) {
        return sendBatch(messages);
    }
}
