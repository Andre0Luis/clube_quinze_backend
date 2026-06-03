package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.admin.DashboardSummary;
import br.com.clube_quinze.api.service.admin.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Dashboard Administrativo")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /** GET /api/v1/admin/dashboard — chamado pelo app mobile */
    @GetMapping
    @Operation(summary = "Resumo do dashboard administrativo")
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<DashboardSummary> getDashboard(
            @RequestParam(defaultValue = "5") int upcomingAppointmentsLimit,
            @RequestParam(defaultValue = "5") int upcomingPaymentsLimit,
            @RequestParam(defaultValue = "30") int renewalWindowDays) {
        return ResponseEntity.ok(adminDashboardService.getSummary(
                upcomingAppointmentsLimit, upcomingPaymentsLimit, renewalWindowDays));
    }

    /** GET /api/v1/admin/dashboard/summary — alias mantido para compatibilidade */
    @GetMapping("/summary")
    @Operation(summary = "Resumo do dashboard administrativo (alias)")
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<DashboardSummary> getSummary(
            @RequestParam(defaultValue = "5") int upcomingAppointmentsLimit,
            @RequestParam(defaultValue = "5") int upcomingPaymentsLimit,
            @RequestParam(defaultValue = "30") int renewalWindowDays) {
        return ResponseEntity.ok(adminDashboardService.getSummary(
                upcomingAppointmentsLimit, upcomingPaymentsLimit, renewalWindowDays));
    }
}
