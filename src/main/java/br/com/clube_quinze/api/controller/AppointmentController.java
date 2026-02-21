package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.appointment.AppointmentRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentRescheduleRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentResponse;
import br.com.clube_quinze.api.dto.appointment.AppointmentStatusUpdateRequest;
import br.com.clube_quinze.api.dto.appointment.AvailableSlotResponse;
import br.com.clube_quinze.api.dto.common.PageResponse;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.security.ClubeQuinzeUserDetails;
import br.com.clube_quinze.api.service.appointment.AppointmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Agendamentos")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/availability")
    public ResponseEntity<AvailableSlotResponse> listAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "tier", required = false) MembershipTier tier) {
        return ResponseEntity.ok(appointmentService.getAvailableSlots(date, tier));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> schedule(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @Valid @RequestBody AppointmentRequest request) {
        ClubeQuinzeUserDetails user = requireAuthenticated(currentUser);
        boolean privileged = isPrivileged(user);
        AppointmentRequest normalizedRequest = privileged
            ? request
            : new AppointmentRequest(
                user.getId(),
                request.scheduledAt(),
                request.appointmentTier(),
                request.serviceType(),
                request.notes(),
                request.durationMinutes());
        AppointmentResponse response =
                appointmentService.schedule(user.getId(), privileged, normalizedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{appointmentId}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @Valid @RequestBody AppointmentRescheduleRequest request) {
        ClubeQuinzeUserDetails user = requireAuthenticated(currentUser);
        boolean privileged = isPrivileged(user);
        AppointmentResponse response = appointmentService.reschedule(
                appointmentId,
                user.getId(),
                privileged,
                request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long appointmentId,
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser) {
        ClubeQuinzeUserDetails user = requireAuthenticated(currentUser);
        boolean privileged = isPrivileged(user);
        appointmentService.cancel(appointmentId, user.getId(), privileged);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{appointmentId}/status")
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentStatusUpdateRequest request) {
        return ResponseEntity.ok(appointmentService.updateStatus(appointmentId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<AppointmentResponse>> listMyAppointments(
            @AuthenticationPrincipal ClubeQuinzeUserDetails currentUser,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ClubeQuinzeUserDetails user = requireAuthenticated(currentUser);
        PageResponse<AppointmentResponse> response = appointmentService.getAppointmentsForUser(
                user.getId(), status, startDate, endDate, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<PageResponse<AppointmentResponse>> listAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<AppointmentResponse> response = appointmentService.getAppointments(
                status, clientId, startDate, endDate, page, size);
        return ResponseEntity.ok(response);
    }

    private ClubeQuinzeUserDetails requireAuthenticated(ClubeQuinzeUserDetails currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("Usuário não autenticado");
        }
        return currentUser;
    }

    private boolean isPrivileged(ClubeQuinzeUserDetails currentUser) {
        RoleType role = currentUser.getRole();
        return role == RoleType.CLUB_ADMIN || role == RoleType.CLUB_EMPLOYE;
    }
}
