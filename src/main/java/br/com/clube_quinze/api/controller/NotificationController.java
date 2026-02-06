package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.notifications.PushTokenRequest;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.notification.PushToken;
import br.com.clube_quinze.api.repository.PushTokenRepository;
import br.com.clube_quinze.api.security.ClubeQuinzeUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final PushTokenRepository pushTokenRepository;

    public NotificationController(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
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

        PushToken token = pushTokenRepository.findByToken(request.token())
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
}
