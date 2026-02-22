package br.com.clube_quinze.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;

import br.com.clube_quinze.api.dto.payment.PlanRequest;
import br.com.clube_quinze.api.dto.payment.PlanResponse;
import br.com.clube_quinze.api.service.payment.PlanService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlanControllerTest {

    @Mock
    private PlanService planService;

    @InjectMocks
    private PlanController planController;

    private PlanRequest request;
    private PlanResponse response;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        request = new PlanRequest();
        request.setName("Plano Padrão");
        request.setPrice(new BigDecimal("99.90"));
        request.setDurationMonths(12);
        response = new PlanResponse();
        response.setId(1L);
        response.setName(request.getName());
        response.setPrice(request.getPrice());
        response.setDurationMonths(request.getDurationMonths());
    }

    @Test
    void createPlanShouldReturnCreatedPlan() {
        given(planService.createPlan(any(PlanRequest.class))).willReturn(response);

        ResponseEntity<PlanResponse> entity = planController.createPlan(request);

        then(planService).should().createPlan(any(PlanRequest.class));
        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals(1L, entity.getBody().getId());
    }

    @Test
    void updatePlanShouldReturnUpdatedPlan() {
        given(planService.updatePlan(eq(1L), any(PlanRequest.class))).willReturn(response);

        ResponseEntity<PlanResponse> entity = planController.updatePlan(1L, request);

        then(planService).should().updatePlan(eq(1L), any(PlanRequest.class));
        assertEquals(HttpStatus.OK, entity.getStatusCode());
    }

    @Test
    void listPlansShouldReturnPlans() {
        given(planService.listPlans()).willReturn(List.of(response));

        ResponseEntity<List<PlanResponse>> entity = planController.listPlans();

        then(planService).should().listPlans();
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals(1, entity.getBody().size());
    }

    @Test
    void getPlanShouldReturnPlan() {
        given(planService.getPlan(1L)).willReturn(response);

        ResponseEntity<PlanResponse> entity = planController.getPlan(1L);

        then(planService).should().getPlan(1L);
        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals(1L, entity.getBody().getId());
    }

    @Test
    void deletePlanShouldReturnNoContent() {
        doNothing().when(planService).deletePlan(1L);

        ResponseEntity<Void> entity = planController.deletePlan(1L);

        then(planService).should().deletePlan(1L);
        assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
    }
}
