package br.com.clube_quinze.api.dto.payment;

import java.io.Serializable;

import br.com.clube_quinze.api.dto.payment.PlanSummary;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import java.time.LocalDate;
import java.util.List;

public record PlanRenewalCandidateResponse(
        Long userId,
        String userName,
        MembershipTier membershipTier,
        PlanSummary plan,
        LocalDate planRenewalDate,
        LocalDate planEndDate,
        List<Integer> allowedDurations) implements Serializable {
}