package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	List<User> findAllByOrderByNameAsc();

	List<User> findByPlan_NameContainingIgnoreCaseOrderByNameAsc(String planName);

	List<User> findByMembershipTierOrderByNameAsc(MembershipTier membershipTier);

	List<User> findByRole(RoleType role);
}
