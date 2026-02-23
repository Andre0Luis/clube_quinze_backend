package br.com.clube_quinze.api.service.payment;

import br.com.clube_quinze.api.dto.payment.PlanRenewalCandidateResponse;
import br.com.clube_quinze.api.dto.payment.PlanSummary;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRenewalService {

    private static final List<Integer> ALLOWED_DURATIONS = List.of(1, 3, 6, 12);

    private final UserRepository userRepository;
    private final Clock clock;

    public PaymentRenewalService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanRenewalCandidateResponse> listUpcomingRenewals(int windowDays) {
        LocalDate start = LocalDate.now(clock);
        LocalDate end = start.plusDays(windowDays);
        return userRepository.findByPlanEndDateBetweenAndActiveTrueOrderByPlanEndDateAsc(start, end)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PlanRenewalCandidateResponse toResponse(User user) {
        PlanSummary planSummary = Optional.ofNullable(user.getPlan())
                .map(plan -> new PlanSummary(plan.getId(), plan.getName(), plan.getDescription(), plan.getPrice(), plan.getDurationMonths()))
                .orElse(null);
        return new PlanRenewalCandidateResponse(
                user.getId(),
                user.getName(),
                user.getMembershipTier(),
                planSummary,
                user.getPlanRenewalDate(),
                user.getPlanEndDate(),
                ALLOWED_DURATIONS);
    }
}