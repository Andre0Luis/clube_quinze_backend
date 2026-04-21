package br.com.clube_quinze.api.service.admin;

import br.com.clube_quinze.api.dto.admin.DashboardSummary;
import br.com.clube_quinze.api.dto.admin.PaymentForecastCard;
import br.com.clube_quinze.api.dto.admin.UpcomingAppointmentCard;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.PaymentStatus;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.PaymentRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private static final Set<RoleType> CLIENT_ROLES = Set.of(RoleType.CLUB_STANDARD, RoleType.CLUB_SELECT);

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public AdminDashboardService(
            UserRepository userRepository,
            AppointmentRepository appointmentRepository,
            PaymentRepository paymentRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary(int upcomingAppointmentsLimit, int upcomingPaymentsLimit, int renewalWindowDays) {
        int safeAppointmentsLimit = Math.max(upcomingAppointmentsLimit, 1);
        int safePaymentsLimit = Math.max(upcomingPaymentsLimit, 1);
        int safeRenewalWindowDays = Math.max(renewalWindowDays, 1);

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEndExclusive = monthStart.plusMonths(1);

        long totalClients = userRepository.countByRoles(CLIENT_ROLES);
        long activePlans = userRepository.countActivePlans(today, CLIENT_ROLES);

        BigDecimal monthlyRevenue = paymentRepository.sumAmountByStatusAndPaidAtBetween(
                PaymentStatus.CONFIRMED,
                monthStart,
                monthEndExclusive);

        List<UpcomingAppointmentCard> upcomingAppointments = appointmentRepository
                .findUpcomingByStatus(
                        AppointmentStatus.SCHEDULED,
                        now,
                        PageRequest.of(0, safeAppointmentsLimit))
                .stream()
                .map(this::toUpcomingAppointmentCard)
                .toList();

        LocalDate renewalLimitDate = today.plusDays(safeRenewalWindowDays);
        List<PaymentForecastCard> upcomingPayments = userRepository
                .findByPlanEndDateBetweenAndActiveTrueOrderByPlanEndDateAsc(today, renewalLimitDate)
                .stream()
                .filter(user -> CLIENT_ROLES.contains(user.getRole()))
                .limit(safePaymentsLimit)
                .map(this::toPaymentForecastCard)
                .toList();

        return new DashboardSummary(
                totalClients,
                activePlans,
                monthlyRevenue,
                upcomingAppointments,
                upcomingPayments);
    }

    private UpcomingAppointmentCard toUpcomingAppointmentCard(Appointment appointment) {
        return new UpcomingAppointmentCard(
                appointment.getId(),
                appointment.getClient().getId(),
                appointment.getClient().getName(),
                appointment.getScheduledAt(),
                appointment.getServiceType());
    }

    private PaymentForecastCard toPaymentForecastCard(User user) {
        BigDecimal amount = user.getPlan() != null ? user.getPlan().getPrice() : BigDecimal.ZERO;
        return new PaymentForecastCard(user.getId(), user.getName(), amount, user.getPlanEndDate());
    }
}
