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
        return sendBatch(messages, false);
    }

    @Override
    public List<ExpoResult> sendBatch(List<ExpoMessage> messages, boolean dryRun) {
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
            BatchResponse batch = firebaseMessaging.sendEach(fcmMessages, dryRun);
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
                    // Log por mensagem com token mascarado: é o que faltava para debugar a causa real.
                    log.warn("FCM send failed (dryRun={}) token={} code={} httpResponse={} msg={}",
                            dryRun, maskToken(messages.get(i).to()), code,
                            httpStatus(ex), ex.getMessage());
                    results.add(new ExpoResult(false, "error", code));
                    invalidateIfStale(messages.get(i).to(), ex.getMessagingErrorCode());
                }
            }
            log.info("FCM batch (dryRun={}): {} sent, {} failed", dryRun,
                    batch.getSuccessCount(), batch.getFailureCount());
            return results;

        } catch (FirebaseMessagingException e) {
            // Falha global (ex.: credencial inválida / projeto errado) — afeta TODAS as mensagens.
            String code = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : "UNKNOWN";
            log.error("FCM sendEach failed globally (dryRun={}) code={} msg={}", dryRun, code, e.getMessage(), e);
            List<ExpoResult> failures = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                failures.add(new ExpoResult(false, "error", code + ": " + e.getMessage()));
            }
            return failures;
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 12) return "***";
        return token.substring(0, 6) + "…" + token.substring(token.length() - 6);
    }

    private String httpStatus(FirebaseMessagingException ex) {
        return ex.getHttpResponse() != null ? String.valueOf(ex.getHttpResponse().getStatusCode()) : "n/a";
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
