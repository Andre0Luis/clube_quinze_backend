package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.settings.AdminNotificationSettings;
import br.com.clube_quinze.api.service.settings.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@Tag(name = "Configurações Administrativas")
public class AdminSettingsController {

    private final SettingsService settingsService;

    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** Lembretes que o admin recebe antes de cada atendimento. */
    @GetMapping("/notifications")
    @Operation(summary = "Obter configuração de lembretes do admin")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<AdminNotificationSettings> getNotificationSettings() {
        return ResponseEntity.ok(new AdminNotificationSettings(
                settingsService.isAdminReminderEnabled(),
                settingsService.getAdminReminderOffsets()));
    }

    @PutMapping("/notifications")
    @Operation(summary = "Atualizar configuração de lembretes do admin")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<AdminNotificationSettings> updateNotificationSettings(
            @Valid @RequestBody AdminNotificationSettings request) {
        settingsService.updateAdminReminderSettings(request.enabled(), request.offsets());
        return ResponseEntity.ok(new AdminNotificationSettings(
                settingsService.isAdminReminderEnabled(),
                settingsService.getAdminReminderOffsets()));
    }
}
