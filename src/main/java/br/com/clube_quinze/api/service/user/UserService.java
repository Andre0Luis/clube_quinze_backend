package br.com.clube_quinze.api.service.user;

import br.com.clube_quinze.api.dto.user.UpdateUserRequest;
import br.com.clube_quinze.api.dto.user.PlanChangeRequest;
import br.com.clube_quinze.api.dto.user.PlanRenewRequest;
import br.com.clube_quinze.api.dto.user.UserProfileResponse;
import br.com.clube_quinze.api.dto.user.UserSummary;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import java.util.List;

public interface UserService {

    List<UserSummary> listMembers(String membershipTierFilter);

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateUserRequest request);

    UserProfileResponse changePlan(Long userId, PlanChangeRequest request);

    UserProfileResponse renewPlan(Long userId, PlanRenewRequest request);

    void deleteUser(Long actorId, RoleType actorRole, Long targetUserId);
}
