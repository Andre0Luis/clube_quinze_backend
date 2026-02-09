package br.com.clube_quinze.api.service.appointment.impl;

import br.com.clube_quinze.api.dto.appointment.AppointmentRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentRescheduleRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentResponse;
import br.com.clube_quinze.api.dto.appointment.AppointmentStatusUpdateRequest;
import br.com.clube_quinze.api.dto.appointment.AvailableSlotResponse;
import br.com.clube_quinze.api.dto.common.PageResponse;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.MembershipTier;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.service.appointment.AppointmentService;
import br.com.clube_quinze.api.util.PageUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSING_TIME = LocalTime.of(21, 0);
    private static final Duration SLOT_DURATION = Duration.ofMinutes(30);

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final br.com.clube_quinze.api.service.notification.PushNotificationService pushNotificationService;
    private final Clock clock;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository, UserRepository userRepository, Clock clock,
            br.com.clube_quinze.api.service.notification.PushNotificationService pushNotificationService) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.pushNotificationService = pushNotificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailableSlotResponse getAvailableSlots(LocalDate date, MembershipTier tier) {
        MembershipTier effectiveTier = tier == null ? MembershipTier.CLUB_15 : tier;
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            return new AvailableSlotResponse(date, effectiveTier, List.of());
        }

        LocalDateTime dayStart = date.atTime(OPENING_TIME);
        LocalDateTime dayEnd = date.atTime(CLOSING_TIME);

        List<Appointment> appointments = appointmentRepository.findByScheduledAtBetween(dayStart, dayEnd);
        Set<LocalDateTime> occupiedSlots = new HashSet<>();
        for (Appointment appointment : appointments) {
            occupiedSlots.add(appointment.getScheduledAt());
        }

        List<LocalDateTime> available = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime slot = dayStart;
        LocalDateTime lastPossibleSlot = dayEnd.minus(SLOT_DURATION);
        while (!slot.isAfter(lastPossibleSlot)) {
            if (slot.isAfter(now) && !occupiedSlots.contains(slot)) {
                available.add(slot);
            }
            slot = slot.plus(SLOT_DURATION);
        }

        return new AvailableSlotResponse(date, effectiveTier, available);
    }

    @Override
    @Transactional
    public AppointmentResponse schedule(Long actorId, boolean privileged, AppointmentRequest request) {
        enforceSelfService(actorId, privileged, request.clientId());

        User client = findUser(request.clientId());
        validateClientForTier(client, request.appointmentTier());
        validateAppointmentDate(request.scheduledAt());
        validateBusinessHours(request.scheduledAt());
        ensureSlotAvailable(request.scheduledAt(), null);

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setScheduledAt(request.scheduledAt());
        appointment.setAppointmentTier(request.appointmentTier());
        appointment.setServiceType(request.serviceType());
        appointment.setNotes(request.notes());
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        Appointment saved = appointmentRepository.save(appointment);
        // best-effort push confirmation
        try {
            var data = new java.util.HashMap<String, Object>();
            data.put("appointmentId", saved.getId());
            data.put("scheduledAt", saved.getScheduledAt().toString());
            pushNotificationService.sendToUser(saved.getClient().getId(), "SCHEDULED",
                    "Agendamento confirmado",
                    "Seu agendamento foi criado para " + saved.getScheduledAt().toString(), data);
        } catch (Exception ignored) {
        }
        return toAppointmentResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(
            Long appointmentId,
            Long actorId,
            boolean privileged,
            AppointmentRescheduleRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        enforceOwnership(appointment, actorId, privileged);

        validateAppointmentDate(request.newDate());
        validateBusinessHours(request.newDate());
        ensureSlotAvailable(request.newDate(), appointment.getId());

        appointment.setScheduledAt(request.newDate());
        if (request.notes() != null && !request.notes().isBlank()) {
            appointment.setNotes(request.notes());
        }
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        Appointment updated = appointmentRepository.save(appointment);
        // best-effort push confirmation
        try {
            var data = new java.util.HashMap<String, Object>();
            data.put("appointmentId", updated.getId());
            data.put("scheduledAt", updated.getScheduledAt().toString());
            pushNotificationService.sendToUser(updated.getClient().getId(), "RESCHEDULED",
                    "Agendamento remarcado",
                    "Seu agendamento foi remarcado para " + updated.getScheduledAt().toString(), data);
        } catch (Exception ignored) {
        }
        return toAppointmentResponse(updated);
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(Long appointmentId, AppointmentStatusUpdateRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        appointment.setStatus(request.status());
        if (request.notes() != null && !request.notes().isBlank()) {
            appointment.setNotes(request.notes());
        }
        Appointment updated = appointmentRepository.save(appointment);
        // notify key status transitions
        try {
            if (request.status() == AppointmentStatus.CANCELED) {
                var data = java.util.Map.<String, Object>of("appointmentId", updated.getId());
                pushNotificationService.sendToUser(updated.getClient().getId(), "CANCELLED",
                        "Agendamento cancelado", "Seu agendamento foi cancelado pelo clube.", data);
            }
        } catch (Exception ignored) {
        }
        return toAppointmentResponse(updated);
    }

    @Override
    @Transactional
    public void cancel(Long appointmentId, Long actorId, boolean privileged) {
        Appointment appointment = findAppointment(appointmentId);
        enforceOwnership(appointment, actorId, privileged);
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointmentRepository.save(appointment);
        // send immediate push notification to user about cancellation (best-effort, async)
        try {
            var data = java.util.Map.<String, Object>of("appointmentId", appointment.getId());
            pushNotificationService.sendToUser(appointment.getClient().getId(), "CANCELLED",
                    "Agendamento cancelado", "Seu agendamento foi cancelado pelo clube.", data);
        } catch (Exception ex) {
            // do not break cancel flow on notification errors
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getAppointmentsForUser(
            Long userId,
            AppointmentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {
        return searchAppointments(status, userId, startDate, endDate, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getAppointments(
            AppointmentStatus status,
            Long clientId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {
        return searchAppointments(status, clientId, startDate, endDate, page, size);
    }

    private PageResponse<AppointmentResponse> searchAppointments(
            AppointmentStatus status,
            Long clientId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size) {
    Specification<Appointment> specification = (root, query, builder) -> builder.conjunction();

        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }

        if (clientId != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.get("client").get("id"), clientId));
        }

        if (startDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            specification = specification.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("scheduledAt"), startDateTime));
        }

        if (endDate != null) {
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
            specification = specification.and(
                    (root, query, builder) -> builder.lessThanOrEqualTo(root.get("scheduledAt"), endDateTime));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "scheduledAt"));
        Page<AppointmentResponse> appointmentPage = appointmentRepository.findAll(specification, pageable)
                .map(this::toAppointmentResponse);

        return PageUtils.toResponse(appointmentPage);
    }

    private void ensureSlotAvailable(LocalDateTime scheduledAt, Long appointmentIdToIgnore) {
        boolean conflict;
        if (appointmentIdToIgnore == null) {
            conflict = appointmentRepository.existsByScheduledAt(scheduledAt);
        } else {
            conflict = appointmentRepository.existsByScheduledAtAndIdNot(scheduledAt, appointmentIdToIgnore);
        }

        if (conflict) {
            throw new BusinessException("Horário indisponível para agendamento");
        }
    }

    private void validateAppointmentDate(LocalDateTime scheduledAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!scheduledAt.isAfter(now)) {
            throw new BusinessException("O agendamento deve ser em uma data futura");
        }
    }

    private void validateBusinessHours(LocalDateTime scheduledAt) {
        LocalTime time = scheduledAt.toLocalTime();
        LocalTime lastSlotStart = CLOSING_TIME.minusMinutes(SLOT_DURATION.toMinutes());
        if (time.isBefore(OPENING_TIME) || time.isAfter(lastSlotStart)) {
            throw new BusinessException("Horário fora do expediente do clube");
        }
    }

    private void validateClientForTier(User client, MembershipTier tier) {
        if (tier == MembershipTier.QUINZE_SELECT && client.getMembershipTier() != MembershipTier.QUINZE_SELECT) {
            throw new BusinessException("Somente membros Quinze Select podem agendar neste atendimento");
        }
        if (!client.isActive()) {
            throw new BusinessException("Usuário inativo não pode realizar agendamento");
        }
    }

    private void enforceSelfService(Long actorId, boolean privileged, Long clientId) {
        if (!privileged && !clientId.equals(actorId)) {
            throw new UnauthorizedException("Não é permitido agendar para outro usuário");
        }
    }

    private void enforceOwnership(Appointment appointment, Long actorId, boolean privileged) {
        if (privileged) {
            return;
        }
        if (!appointment.getClient().getId().equals(actorId)) {
            throw new UnauthorizedException("Não é permitido manipular este agendamento");
        }
    }

    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private AppointmentResponse toAppointmentResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getClient().getId(),
                appointment.getScheduledAt(),
                appointment.getAppointmentTier(),
                appointment.getStatus(),
                appointment.getServiceType(),
                appointment.getNotes());
    }
}