package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.notifications.PushTokenRequest;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import br.com.clube_quinze.api.security.ClubeQuinzeUserDetails;
import br.com.clube_quinze.api.service.notification.ExpoPushService;
import br.com.clube_quinze.api.service.notification.PushNotificationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final PushTokenRepository pushTokenRepository;
    private final PushNotificationService pushNotificationService;
    private final ExpoPushService expoPushService;

    public NotificationController(PushTokenRepository pushTokenRepository,
                                  PushNotificationService pushNotificationService,
                                  ExpoPushService expoPushService) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushNotificationService = pushNotificationService;
        this.expoPushService = expoPushService;
    }

    @PostMapping("/tokens")
    public ResponseEntity<Void> registerToken(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @Valid @RequestBody PushTokenRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("Usuário não autenticado");
        }
        Long userId = currentUser.getId();

        // Invalidate duplicate token entries and upsert
        pushTokenRepository.findByToken(request.token()).ifPresent(existing -> {
            if (!existing.getUserId().equals(userId)) {
                existing.setInvalidatedAt(Instant.now());
                pushTokenRepository.save(existing);
            }
        });

        pushTokenRepository.findByToken(request.token())
                .map(existing -> {
                    existing.setInvalidatedAt(null);
                    existing.setPlatform(request.platform());
                    existing.setLastSuccessAt(null);
                    return pushTokenRepository.save(existing);
                })
                .orElseGet(() -> {
                    PushToken t = new PushToken();
                    t.setUserId(userId);
                    t.setToken(request.token());
                    t.setPlatform(request.platform());
                    return pushTokenRepository.save(t);
                });

        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint de diagnóstico FCM. Admin-only. Valida a integração Firebase end-to-end e
     * retorna o resultado real por token (não mascara o erro).
     *
     * <p>Use {@code ?dryRun=true} para validar credencial + token sem entregar a notificação
     * ao dispositivo (suportado pelo FCM). Útil para provar que o pipeline está correto sem
     * spammar um device real.
     *
     * Exemplos:
     *   POST /api/v1/notifications/test/42            (entrega de verdade)
     *   POST /api/v1/notifications/test/42?dryRun=true (só valida)
     *   Authorization: Bearer &lt;admin-token&gt;
     */
    @PostMapping("/test/{userId}")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTest(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        List<PushToken> tokens = pushTokenRepository.findByUserIdAndInvalidatedAtIsNull(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("dryRun", dryRun);
        response.put("activeTokens", tokens.size());

        if (tokens.isEmpty()) {
            response.put("status", "no-active-tokens");
            response.put("results", List.of());
            return ResponseEntity.ok(response);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("kind", "test");
        data.put("sentAt", Instant.now().toString());

        List<ExpoPushService.ExpoMessage> messages = new ArrayList<>();
        for (PushToken t : tokens) {
            messages.add(new ExpoPushService.ExpoMessage(
                    t.getToken(),
                    "🚀 Notificação de teste",
                    "Se você viu isso, a integração FCM está funcionando!",
                    data));
        }

        // Caminho direto pelo provedor: expõe o resultado cru por token (ok + errorCode FCM).
        List<ExpoPushService.ExpoResult> results = expoPushService.sendBatch(messages, dryRun);

        List<Map<String, Object>> perToken = new ArrayList<>();
        int ok = 0;
        for (int i = 0; i < results.size(); i++) {
            ExpoPushService.ExpoResult r = results.get(i);
            if (r.ok()) ok++;
            Map<String, Object> entry = new HashMap<>();
            entry.put("tokenId", tokens.get(i).getId());
            entry.put("platform", tokens.get(i).getPlatform());
            entry.put("token", maskToken(tokens.get(i).getToken()));
            entry.put("ok", r.ok());
            entry.put("error", r.ok() ? null : r.message());
            perToken.add(entry);
        }

        response.put("sent", ok);
        response.put("failed", results.size() - ok);
        response.put("status", ok > 0 ? "ok" : "all-failed");
        response.put("results", perToken);
        return ResponseEntity.ok(response);
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 12) return "***";
        return token.substring(0, 6) + "…" + token.substring(token.length() - 6);
    }
}
