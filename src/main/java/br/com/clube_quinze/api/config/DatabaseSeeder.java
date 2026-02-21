package br.com.clube_quinze.api.config;

import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.payment.Plan;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.PlanRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(
            PlanRepository planRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Plan defaultPlan = planRepository
                .findByName("Plano Padrão")
                .orElseGet(this::createDefaultPlan);

        userRepository
                .findByEmail("aluis283@gmail.com")
                .orElseGet(() -> createAdminUser(defaultPlan));
    }

    private Plan createDefaultPlan() {
        Plan plan = new Plan();
        plan.setName("Plano Padrão");
        plan.setDescription("Plano padrão inicial do Clube Quinze");
        plan.setPrice(new BigDecimal("99.90"));
        plan.setDurationMonths(12);
        Plan saved = planRepository.save(plan);
        log.info("Plano padrão criado com ID {}", saved.getId());
        return saved;
    }

    private User createAdminUser(Plan defaultPlan) {
        User admin = new User();
        admin.setName("André Luis");
        admin.setEmail("aluis283@gmail.com");
        admin.setPasswordHash(passwordEncoder.encode("luizinho@01"));
        admin.setRole(RoleType.CLUB_ADMIN);
        admin.setMembershipTier(MembershipTier.QUINZE_STANDARD);
        admin.setPlan(defaultPlan);
        admin.setActive(true);
        User saved = userRepository.save(admin);
        log.info("Usuário administrador padrão criado com ID {}", saved.getId());
        return saved;
    }
}
