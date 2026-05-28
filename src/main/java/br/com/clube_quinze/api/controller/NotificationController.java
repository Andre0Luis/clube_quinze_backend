package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.notifications.PushTokenRequest;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import br.com.clube_quinze.api.security.ClubeQuinzeUserDetails;
import br.com.clube_quinze.api.service.notification.PushNotificationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final PushTokenRepository pushTokenRepository;
    private final PushNotificationService pushNotificationService;

    public NotificationController(PushTokenRepository pushTokenRepository,
                                  PushNotificationService pushNotificationService) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushNotificationService = pushNotificationService;
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
     * Envia uma notificação de teste via FCM para o usuário informado.
     * Admin-only. Útil para validar a integração Firebase end-to-end.
     *
     * Exemplo:
     *   POST /api/v1/notifications/test/42
     *   Authorization: Bearer <admin-token>
     */
    @PostMapping("/test/{userId}")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTest(@PathVariable Long userId) {
        long activeTokens = pushTokenRepository
                .findByUserIdAndInvalidatedAtIsNull(userId)
                .size();

        Map<String, Object> data = new HashMap<>();
        data.put("kind", "test");
        data.put("sentAt", Instant.now().toString());

        pushNotificationService.sendToUser(
                userId,
                "TEST",
                "🚀 Notificação de teste",
                "Se você viu isso, a integração FCM está funcionando!",
                data
        );

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("activeTokens", activeTokens);
        response.put("status", activeTokens > 0 ? "dispatched" : "no-active-tokens");
        return ResponseEntity.ok(response);
    }
}
