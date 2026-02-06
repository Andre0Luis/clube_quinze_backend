package br.com.clube_quinze.api.service.notification.impl;

import br.com.clube_quinze.api.service.notification.ExpoPushService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import br.com.clube_quinze.api.model.notification.PushToken;
import java.util.Optional;
import org.springframework.web.client.RestTemplate;

@Service
public class ExpoPushServiceImpl implements ExpoPushService {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushServiceImpl.class);
    private final RestTemplate rest = new RestTemplate();
    private final String endpoint = "https://exp.host/--/api/v2/push/send";
    private final PushTokenRepository pushTokenRepository;

    @Value("${app.media.base-url:}")
    private String baseUrl;

    public ExpoPushServiceImpl(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    @Override
    public List<ExpoResult> sendBatch(List<ExpoMessage> messages) {
        List<ExpoResult> results = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();
        for (ExpoMessage m : messages) {
            var p = Map.of(
                    "to", m.to(),
                    "title", m.title(),
                    "body", m.body(),
                    "sound", "default",
                    "data", m.data() == null ? Map.of() : m.data()
            );
            payloads.add(p);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<Object>> entity = new HttpEntity<>(payloads, headers);
            ResponseEntity<ExpoResponse[]> resp = rest.postForEntity(endpoint, entity, ExpoResponse[].class);
            ExpoResponse[] body = resp.getBody();
            if (body != null) {
                for (int i = 0; i < body.length; i++) {
                    ExpoResponse r = body[i];
                    boolean ok = "ok".equalsIgnoreCase(r.status);
                    String msg = r.message;
                    results.add(new ExpoResult(ok, r.status, msg));
                    // If message indicates device invalid, attempt to mark token invalid in DB (best-effort)
                    try {
                        String to = (String)((Map)payloads.get(i)).get("to");
                        if (msg != null) {
                            String low = msg.toLowerCase();
                            if (low.contains("devicenotregistered") || low.contains("device not registered") || low.contains("invalidcredentials") || low.contains("messagetoo big")) {
                                Optional<PushToken> maybe = pushTokenRepository.findByToken(to);
                                maybe.ifPresent(t -> {
                                    t.setInvalidatedAt(Instant.now());
                                    pushTokenRepository.save(t);
                                });
                            }
                        }
                    } catch (Exception ex) {
                        // ignore - marking invalid is best-effort
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Erro ao enviar push batch: {}", ex.getMessage());
            // mark all as failed
            for (int i = 0; i < messages.size(); i++) {
                results.add(new ExpoResult(false, "error", ex.getMessage()));
            }
        }
        return results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ExpoResponse {
        @JsonProperty("status")
        public String status;
        @JsonProperty("message")
        public String message;
    }
}
