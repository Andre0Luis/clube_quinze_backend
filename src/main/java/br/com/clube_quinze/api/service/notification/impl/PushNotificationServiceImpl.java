package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.model.notification.PushDelivery;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.repository.PushDeliveryRepository;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import br.com.clube_quinze.api.service.notification.ExpoPushService;
import br.com.clube_quinze.api.service.notification.PushNotificationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationServiceImpl.class);

    private final PushTokenRepository pushTokenRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final ExpoPushService expoPushService;

    public PushNotificationServiceImpl(
            PushTokenRepository pushTokenRepository,
            PushDeliveryRepository pushDeliveryRepository,
            ExpoPushService expoPushService) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushDeliveryRepository = pushDeliveryRepository;
        this.expoPushService = expoPushService;
    }

    @Override
    @Transactional
    public void sendToUser(Long userId, String kind, String title, String body, Map<String, Object> data) {
        List<PushToken> tokens = pushTokenRepository.findByUserIdAndInvalidatedAtIsNull(userId);
        if (tokens.isEmpty()) return;

        List<ExpoPushService.ExpoMessage> messages = new ArrayList<>();
        for (PushToken t : tokens) {
            messages.add(new ExpoPushService.ExpoMessage(t.getToken(), title, body, data));
        }

        var results = expoPushService.sendBatch(messages);

        // Persist deliveries and mark invalid tokens if necessary
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            PushDelivery d = new PushDelivery();
            PushToken token = tokens.get(i);
            d.setTokenId(token.getId());
            d.setUserId(userId);
            d.setAppointmentId(data != null && data.containsKey("appointmentId") ? ((Number)data.get("appointmentId")).longValue() : null);
            d.setKind(kind);
            d.setTitle(title);
            d.setBody(body);
            d.setData(data != null ? data.toString() : null);
            d.setStatus(r.ok() ? "sent" : "failed");
            d.setErrorMessage(r.message());
            if (r.ok()) {
                d.setSentAt(Instant.now());
                token.setLastSuccessAt(Instant.now());
            } else {
                // Simple heuristics: mark device invalid when message contains DeviceNotRegistered or InvalidCredentials
                String m = r.message() != null ? r.message().toLowerCase() : "";
                if (m.contains("devicenotregistered") || m.contains("device not registered") || m.contains("invalidcredentials") || m.contains("messagetoo big")) {
                    token.setInvalidatedAt(Instant.now());
                }
            }
            pushDeliveryRepository.save(d);
            pushTokenRepository.save(token);
        }
    }
}
