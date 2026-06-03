package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.common.NotificationResponse;
import br.com.clube_quinze.api.dto.notifications.PushTokenRequest;
import br.com.clube_quinze.api.dto.notifications.TestNotificationType;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.notification.Notification;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.repository.NotificationRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final NotificationRepository notificationRepository;

    public NotificationController(PushTokenRepository pushTokenRepository,
                                  PushNotificationService pushNotificationService,
                                  ExpoPushService expoPushService,
                                  NotificationRepository notificationRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushNotificationService = pushNotificationService;
        this.expoPushService = expoPushService;
        this.notificationRepository = notificationRepository;
    }

    /** Lista as notificações in-app do usuário autenticado (mais recentes primeiro). */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> listMyNotifications(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        Long userId = requireUser(currentUser);
        List<NotificationResponse> items = notificationRepository
                .findTop100ByUserIdOrderBySentAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    /** Contagem de não-lidas — para o badge do ícone de notificações. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        Long userId = requireUser(currentUser);
        return ResponseEntity.ok(Map.of("count", notificationRepository.countByUserIdAndReadFalse(userId)));
    }

    /** Marca uma notificação como lida (apenas se pertencer ao usuário). */
    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @PathVariable Long id) {
        Long userId = requireUser(currentUser);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));
        if (n.getUser() == null || !userId.equals(n.getUser().getId())) {
            throw new UnauthorizedException("Notificação não pertence ao usuário");
        }
        if (!n.isRead()) {
            n.setRead(true);
            notificationRepository.save(n);
        }
        return ResponseEntity.noContent().build();
    }

    /** Marca todas as notificações do usuário como lidas. */
    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        Long userId = requireUser(currentUser);
        notificationRepository.markAllAsReadForUser(userId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUser(ClubeQuinzeUserDetails currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("Usuário não autenticado");
        }
        return currentUser.getId();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUser() != null ? n.getUser().getId() : null,
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.isRead(),
                n.getSentAt());
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

    /** Desativa push do usuário: invalida todos os tokens ativos (toggle "desabilitar" no app). */
    @DeleteMapping("/tokens")
    @Transactional
    public ResponseEntity<Void> disableMyTokens(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        Long userId = requireUser(currentUser);
        Instant now = Instant.now();
        pushTokenRepository.findByUserIdAndInvalidatedAtIsNull(userId).forEach(t -> {
            t.setInvalidatedAt(now);
            pushTokenRepository.save(t);
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint de diagnóstico FCM. Admin-only. Valida a integração Firebase end-to-end e
     * retorna o resultado real por token (não mascara o erro).
     *
     * <p>Use {@code ?type} para simular cada tipo de notificação que o sistema envia em produção:
     * <ul>
     *   <li>TEST (padrão) — verifica que o FCM está funcionando</li>
     *   <li>REMINDER_24H / REMINDER_3H / REMINDER_1H / REMINDER_30MIN — lembretes de agendamento</li>
     *   <li>RESCHEDULED — reagendamento</li>
     * </ul>
     *
     * <p>Use {@code ?dryRun=true} para validar credencial + token sem entregar ao dispositivo.
     *
     * Exemplos:
     *   POST /api/v1/notifications/test/42                              (teste simples)
     *   POST /api/v1/notifications/test/42?type=REMINDER_1H             (simula lembrete 1h)
     *   POST /api/v1/notifications/test/42?type=RESCHEDULED&dryRun=true (dry-run reagendamento)
     *   Authorization: Bearer &lt;admin-token&gt;
     */
    @PostMapping("/test/{userId}")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTest(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "TEST") TestNotificationType type,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        List<PushToken> tokens = pushTokenRepository.findByUserIdAndInvalidatedAtIsNull(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("type", type.name());
        response.put("dryRun", dryRun);
        response.put("activeTokens", tokens.size());

        if (tokens.isEmpty()) {
            response.put("status", "no-active-tokens");
            response.put("results", List.of());
            return ResponseEntity.ok(response);
        }

        Map<String, Object> data = new HashMap<>(type.data);
        data.put("sentAt", Instant.now().toString());

        List<ExpoPushService.ExpoMessage> messages = new ArrayList<>();
        for (PushToken t : tokens) {
            messages.add(new ExpoPushService.ExpoMessage(t.getToken(), type.title, type.body, data));
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
