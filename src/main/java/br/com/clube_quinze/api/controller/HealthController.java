package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.health.HealthResponse;
import br.com.clube_quinze.api.service.health.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endereço público de saúde: aplicação, versão, build e o estado das conexões. 200 se a API trabalha; 503 se o banco caiu. */
@RestController
@Tag(name = "Saúde")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @Operation(summary = "Estado da aplicação: versão, build e conexões (banco, fila, push, e-mail, Google)")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthService.check();
        HttpStatus status = "DOWN".equals(response.status()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }
}
