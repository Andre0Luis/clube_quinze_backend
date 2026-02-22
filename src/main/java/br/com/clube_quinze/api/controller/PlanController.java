package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.payment.PlanRequest;
import br.com.clube_quinze.api.dto.payment.PlanResponse;
import br.com.clube_quinze.api.service.payment.PlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Planos")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        PlanResponse created = planService.createPlan(request);
        URI location = URI.create(String.format("/api/v1/plans/%d", created.getId()));
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> listPlans() {
        return ResponseEntity.ok(planService.listPlans());
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(planService.getPlan(planId));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable Long planId,
            @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(planService.updatePlan(planId, request));
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasRole('CLUB_ADMIN')")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        planService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }
}
