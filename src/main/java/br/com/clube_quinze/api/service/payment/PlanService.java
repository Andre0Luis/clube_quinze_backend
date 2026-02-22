package br.com.clube_quinze.api.service.payment;

import br.com.clube_quinze.api.dto.payment.PlanRequest;
import br.com.clube_quinze.api.dto.payment.PlanResponse;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.model.payment.Plan;
import br.com.clube_quinze.api.repository.PlanRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        ensureUniqueName(request.getName(), null);

        Plan plan = new Plan();
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setDurationMonths(request.getDurationMonths());

        Plan saved = planRepository.save(plan);
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse updatePlan(Long id, PlanRequest request) {
        Plan existing = planRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Plano não encontrado"));

        ensureUniqueName(request.getName(), id);

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setDurationMonths(request.getDurationMonths());

        return toResponse(existing);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findAll(Sort.by("name").ascending())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(Long id) {
        Plan plan = planRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Plano não encontrado"));
        return toResponse(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        Plan existing = planRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Plano não encontrado"));
        planRepository.delete(existing);
    }

    private void ensureUniqueName(String name, Long planIdToIgnore) {
        planRepository.findByName(name).ifPresent(found -> {
            if (planIdToIgnore == null || !found.getId().equals(planIdToIgnore)) {
                throw new BusinessException("Plano com esse nome já existe");
            }
        });
    }

    private PlanResponse toResponse(Plan plan) {
        PlanResponse response = new PlanResponse();
        response.setId(plan.getId());
        response.setName(plan.getName());
        response.setDescription(plan.getDescription());
        response.setPrice(plan.getPrice());
        response.setDurationMonths(plan.getDurationMonths());
        return response;
    }
}
