package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.payment.PlanRenewalCandidateResponse;
import br.com.clube_quinze.api.service.payment.PaymentRenewalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Pagamentos")
public class PaymentController {

    private final PaymentRenewalService paymentRenewalService;

    public PaymentController(PaymentRenewalService paymentRenewalService) {
        this.paymentRenewalService = paymentRenewalService;
    }

    @GetMapping("/renewals")
    @Operation(summary = "Listar clientes com planos a vencer")
    @PreAuthorize("hasAnyRole('CLUB_EMPLOYE','CLUB_ADMIN')")
    public ResponseEntity<List<PlanRenewalCandidateResponse>> listUpcomingRenewals(
            @RequestParam(defaultValue = "60") int windowDays) {
        return ResponseEntity.ok(paymentRenewalService.listUpcomingRenewals(windowDays));
    }
}