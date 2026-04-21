package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.user.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	List<User> findAllByOrderByNameAsc();

	List<User> findByPlan_NameContainingIgnoreCaseOrderByNameAsc(String planName);

	List<User> findByMembershipTierOrderByNameAsc(MembershipTier membershipTier);

	List<User> findByRole(RoleType role);

	List<User> findByPlanEndDateBetweenAndActiveTrueOrderByPlanEndDateAsc(LocalDate start, LocalDate end);

	@Query("select count(u) from User u where u.role in :roles")
	long countByRoles(@Param("roles") Collection<RoleType> roles);

	@Query("""
			select count(u)
			from User u
			where u.active = true
			and u.plan is not null
			and u.planEndDate >= :today
			and u.role in :roles
			""")
	long countActivePlans(@Param("today") LocalDate today, @Param("roles") Collection<RoleType> roles);
}
