package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.google.ChooseCalendarRequest;
import br.com.clube_quinze.api.dto.google.GoogleCalendarOption;
import br.com.clube_quinze.api.dto.google.GoogleIntegrationStatus;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.security.ClubeQuinzeUserDetails;
import br.com.clube_quinze.api.service.google.GoogleCalendarReconciler;
import br.com.clube_quinze.api.service.google.GoogleCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configurações do administrador para a integração com o Google Agenda (botão "Conectar" nas configurações). */
@RestController
@RequestMapping("/api/v1/admin/integrations/google")
@Tag(name = "Integração Google Agenda")
@PreAuthorize("hasRole('CLUB_ADMIN')")
public class GoogleIntegrationAdminController {

    private final GoogleCalendarService service;
    private final GoogleCalendarReconciler reconciler;

    public GoogleIntegrationAdminController(GoogleCalendarService service, GoogleCalendarReconciler reconciler) {
        this.service = service;
        this.reconciler = reconciler;
    }

    @GetMapping
    @Operation(summary = "Estado da conexão com o Google Agenda")
    public ResponseEntity<GoogleIntegrationStatus> status() {
        return ResponseEntity.ok(service.status());
    }

    @PostMapping("/connect")
    @Operation(summary = "Inicia a conexão: devolve o endereço de login do Google (abrir no navegador)")
    public ResponseEntity<Map<String, String>> connect(@AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("Usuário não autenticado");
        }
        return ResponseEntity.ok(Map.of("authUrl", service.startConnect(currentUser.getId())));
    }

    @GetMapping("/calendars")
    @Operation(summary = "Agendas da conta conectada (para escolher o destino)")
    public ResponseEntity<List<GoogleCalendarOption>> calendars() {
        return ResponseEntity.ok(service.listCalendars());
    }

    @PutMapping("/calendar")
    @Operation(summary = "Define a agenda de destino: criar uma nova ou usar uma existente")
    public ResponseEntity<GoogleIntegrationStatus> chooseCalendar(@Valid @RequestBody ChooseCalendarRequest request) {
        return ResponseEntity.ok(service.chooseCalendar(request));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sincroniza agora o que ainda não está no Google")
    public ResponseEntity<Map<String, Integer>> syncNow() {
        return ResponseEntity.ok(Map.of("synced", reconciler.reconcileNow()));
    }

    @DeleteMapping
    @Operation(summary = "Desconecta a conta do Google (revoga o acesso e apaga as credenciais)")
    public ResponseEntity<Void> disconnect() {
        service.disconnect();
        return ResponseEntity.noContent().build();
    }
}
