package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.repository.PushTokenRepository;
import br.com.clube_quinze.api.service.notification.ExpoPushService;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FcmPushServiceImpl implements ExpoPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushServiceImpl.class);

    private final FirebaseMessaging firebaseMessaging;
    private final PushTokenRepository pushTokenRepository;

    public FcmPushServiceImpl(FirebaseMessaging firebaseMessaging,
                              PushTokenRepository pushTokenRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.pushTokenRepository = pushTokenRepository;
    }

    @Override
    public List<ExpoResult> sendBatch(List<ExpoMessage> messages) {
        if (messages.isEmpty()) return List.of();

        List<Message> fcmMessages = new ArrayList<>();
        for (ExpoMessage m : messages) {
            fcmMessages.add(Message.builder()
                    .setToken(m.to())
                    .setNotification(Notification.builder()
                            .setTitle(m.title())
                            .setBody(m.body())
                            .build())
                    .putAllData(toStringMap(m.data()))
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build());
        }

        try {
            BatchResponse batch = firebaseMessaging.sendEach(fcmMessages);
            List<ExpoResult> results = new ArrayList<>();

            List<SendResponse> responses = batch.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse r = responses.get(i);
                if (r.isSuccessful()) {
                    results.add(new ExpoResult(true, "ok", null));
                } else {
                    FirebaseMessagingException ex = (FirebaseMessagingException) r.getException();
                    String code = ex.getMessagingErrorCode() != null
                            ? ex.getMessagingErrorCode().name()
                            : "UNKNOWN";
                    results.add(new ExpoResult(false, "error", code));
                    invalidateIfStale(messages.get(i).to(), ex.getMessagingErrorCode());
                }
            }
            log.info("FCM batch: {} sent, {} failed", batch.getSuccessCount(), batch.getFailureCount());
            return results;

        } catch (FirebaseMessagingException e) {
            log.error("FCM sendEach failed: {}", e.getMessage());
            List<ExpoResult> failures = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                failures.add(new ExpoResult(false, "error", e.getMessage()));
            }
            return failures;
        }
    }

    private void invalidateIfStale(String token, MessagingErrorCode errorCode) {
        if (errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            try {
                pushTokenRepository.findByToken(token).ifPresent(t -> {
                    t.setInvalidatedAt(Instant.now());
                    pushTokenRepository.save(t);
                });
            } catch (Exception ex) {
                log.warn("Failed to invalidate token: {}", ex.getMessage());
            }
        }
    }

    private Map<String, String> toStringMap(Map<String, Object> data) {
        if (data == null) return Map.of();
        Map<String, String> result = new HashMap<>();
        data.forEach((k, v) -> {
            if (v != null) result.put(k, v.toString());
        });
        return result;
    }
}
