package br.com.clube_quinze.api.service.user;

import br.com.clube_quinze.api.dto.user.UpdateUserRequest;
import br.com.clube_quinze.api.dto.user.UserProfileResponse;
import br.com.clube_quinze.api.dto.user.UserSummary;
import java.util.List;

public interface UserService {

    List<UserSummary> listMembers(String planFilter);

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateUserRequest request);
}
