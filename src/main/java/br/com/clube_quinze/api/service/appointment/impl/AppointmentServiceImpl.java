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
import br.com.clube_quinze.api.service.appointment.AppointmentScheduleSettings;
import br.com.clube_quinze.api.service.appointment.AppointmentService;
import br.com.clube_quinze.api.config.RabbitMQConfig;
import br.com.clube_quinze.api.dto.notification.NotificationMessageDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import br.com.clube_quinze.api.util.PageUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    private static final LocalTime OPENING_TIME = AppointmentScheduleSettings.OPENING_TIME;
    private static final LocalTime CLOSING_TIME = AppointmentScheduleSettings.CLOSING_TIME;
    private static final Duration SLOT_DURATION = AppointmentScheduleSettings.SLOT_DURATION;
    private static final DateTimeFormatter PT_BR_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository, UserRepository userRepository, Clock clock,
            RabbitTemplate rabbitTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailableSlotResponse getAvailableSlots(LocalDate date, MembershipTier tier) {
        MembershipTier effectiveTier = tier == null ? MembershipTier.QUINZE_STANDARD : tier;
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            return new AvailableSlotResponse(date, effectiveTier, List.of());
        }

        LocalDateTime dayStart = date.atTime(OPENING_TIME);
        LocalDateTime dayEnd = date.atTime(CLOSING_TIME);

        List<Appointment> appointments = appointmentRepository.findByScheduledAtBetween(dayStart, dayEnd);
        Set<LocalDateTime> occupiedSlots = new HashSet<>();
        for (Appointment appointment : appointments) {
            if (appointment.getStatus() != AppointmentStatus.CANCELED) {
                LocalDateTime curr = appointment.getScheduledAt();
                LocalDateTime end = curr.plusMinutes(appointment.getDurationMinutes());
                while (curr.isBefore(end)) {
                    occupiedSlots.add(curr);
                    curr = curr.plusMinutes(SLOT_DURATION.toMinutes());
                }
            }
        }

        Duration durationNeeded = effectiveTier == MembershipTier.QUINZE_SELECT ? Duration.ofMinutes(120) : SLOT_DURATION;
        Duration step = effectiveTier == MembershipTier.QUINZE_SELECT ? Duration.ofMinutes(120) : SLOT_DURATION;

        List<LocalDateTime> available = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime slot = dayStart;
        LocalDateTime lastPossibleSlot = dayEnd.minus(durationNeeded);
        
        while (!slot.isAfter(lastPossibleSlot)) {
            if (slot.isAfter(now) && isIntervalFree(slot, durationNeeded, occupiedSlots)) {
                available.add(slot);
            }
            slot = slot.plus(step);
        }

        return new AvailableSlotResponse(date, effectiveTier, available);
    }

    private boolean isIntervalFree(LocalDateTime start, Duration duration, Set<LocalDateTime> occupied) {
        LocalDateTime curr = start;
        LocalDateTime end = start.plus(duration);
        while (curr.isBefore(end)) {
            if (occupied.contains(curr)) {
                return false;
            }
            curr = curr.plus(SLOT_DURATION);
        }
        return true;
    }

    @Override
    @Transactional
    public AppointmentResponse schedule(Long actorId, boolean privileged, AppointmentRequest request) {
        enforceSelfService(actorId, privileged, request.clientId());

        User client = findUser(request.clientId());
        validateClientForTier(client, request.appointmentTier());
        validateAppointmentDate(request.scheduledAt());
        validateBusinessHours(request.scheduledAt());
        
        int duration = resolveDuration(request);
        ensureSlotAvailable(request.scheduledAt(), duration, null);

        if (Boolean.TRUE.equals(request.recurring()) && request.recurrencePeriod() != null && request.recurrenceMonths() != null) {
            return scheduleRecurring(client, request, duration);
        }

        Appointment saved = createSingleAppointment(client, request, request.scheduledAt(), duration, null, null);
        sendSchedulePush(saved);
        return toAppointmentResponse(saved);
    }

    private Appointment createSingleAppointment(User client, AppointmentRequest request, LocalDateTime date, int duration, String groupId, String period) {
        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setScheduledAt(date);
        appointment.setAppointmentTier(request.appointmentTier());
        appointment.setServiceType(request.serviceType());
        appointment.setNotes(request.notes());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setDurationMinutes(duration);
        appointment.setRecurrenceGroupId(groupId);
        appointment.setRecurrencePeriod(period);
        return appointmentRepository.save(appointment);
    }

    private AppointmentResponse scheduleRecurring(User client, AppointmentRequest request, int duration) {
        String groupId = java.util.UUID.randomUUID().toString();
        String period = request.recurrencePeriod();
        int months = request.recurrenceMonths();
        
        LocalDateTime current = request.scheduledAt();
        LocalDateTime endLimit = current.plusMonths(months);
        
        Appointment firstAppointment = null;
        
        while (current.isBefore(endLimit)) {
            try {
                // Ensure slot is available before attempting to create
                ensureSlotAvailable(current, duration, null);
                Appointment saved = createSingleAppointment(client, request, current, duration, groupId, period);
                if (firstAppointment == null) {
                    firstAppointment = saved;
                    sendSchedulePush(saved); // Only push for the first one to avoid spam
                }
            } catch (BusinessException e) {
                // Skip if unavailable
            }
            
            if ("WEEKLY".equals(period)) {
                current = current.plusWeeks(1);
            } else if ("BIWEEKLY".equals(period)) {
                current = current.plusWeeks(2);
            } else if ("MONTHLY".equals(period)) {
                current = current.plusMonths(1);
            } else {
                break; // safeguard
            }
        }
        
        if (firstAppointment == null) {
            throw new BusinessException("Nenhum horário disponível para criar a série de agendamentos");
        }
        
        return toAppointmentResponse(firstAppointment);
    }

    private void sendSchedulePush(Appointment saved) {
        try {
            var data = new java.util.HashMap<String, Object>();
            data.put("appointmentId", saved.getId());
            data.put("scheduledAt", saved.getScheduledAt().toString());
            
            var pushMap = new java.util.HashMap<String, Object>();
            pushMap.put("userId", saved.getClient().getId());
            pushMap.put("type", "SCHEDULED");
            pushMap.put("title", "Agendamento confirmado");
            pushMap.put("body", "Seu agendamento foi criado para " + saved.getScheduledAt().toString());
            pushMap.put("data", data);
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("PUSH_MESSAGE", pushMap));
        } catch (Exception ignored) {
        }
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

        // capture old time before update
        LocalDateTime oldScheduledAt = appointment.getScheduledAt();

        validateAppointmentDate(request.newDate());
        validateBusinessHours(request.newDate());
        
        int duration = appointment.getDurationMinutes();
        ensureSlotAvailable(request.newDate(), duration, appointment.getId());

        appointment.setScheduledAt(request.newDate());
        if (request.notes() != null && !request.notes().isBlank()) {
            appointment.setNotes(request.notes());
        }
        appointment.setStatus(AppointmentStatus.SCHEDULED);

        Appointment updated = appointmentRepository.save(appointment);

        // best-effort push + email notification
        User client = updated.getClient();
        String newFormatted = updated.getScheduledAt().format(PT_BR_FORMATTER);
        try {
            var data = new java.util.HashMap<String, Object>();
            data.put("appointmentId", updated.getId());
            data.put("scheduledAt", updated.getScheduledAt().toString());
            
            var pushMap = new java.util.HashMap<String, Object>();
            pushMap.put("userId", client.getId());
            pushMap.put("type", "RESCHEDULED");
            pushMap.put("title", "Agendamento remarcado");
            pushMap.put("body", "Seu agendamento foi remarcado para " + newFormatted);
            pushMap.put("data", data);
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("PUSH_MESSAGE", pushMap));
        } catch (Exception ignored) {
        }

        // send email with old/new times and description
        try {
            String oldFormatted = oldScheduledAt.format(PT_BR_FORMATTER);
            String description = updated.getNotes() != null ? updated.getNotes() : "";
            
            var emailMap = new java.util.HashMap<String, Object>();
            emailMap.put("email", client.getEmail());
            emailMap.put("name", client.getName());
            emailMap.put("oldScheduledAt", oldFormatted);
            emailMap.put("newScheduledAt", newFormatted);
            emailMap.put("description", description);
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("APPOINTMENT_RESCHEDULED_EMAIL", emailMap));
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
                
                var pushMap = new java.util.HashMap<String, Object>();
                pushMap.put("userId", updated.getClient().getId());
                pushMap.put("type", "CANCELLED");
                pushMap.put("title", "Agendamento cancelado");
                pushMap.put("body", "Seu agendamento foi cancelado pelo clube.");
                pushMap.put("data", data);
                
                rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("PUSH_MESSAGE", pushMap));
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
            
            var pushMap = new java.util.HashMap<String, Object>();
            pushMap.put("userId", appointment.getClient().getId());
            pushMap.put("type", "CANCELLED");
            pushMap.put("title", "Agendamento cancelado");
            pushMap.put("body", "Seu agendamento foi cancelado pelo clube.");
            pushMap.put("data", data);
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("PUSH_MESSAGE", pushMap));
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

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long appointmentId, Long actorId, boolean privileged) {
        Appointment appointment = findAppointment(appointmentId);
        enforceOwnership(appointment, actorId, privileged);
        return toAppointmentResponse(appointment);
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

    private void ensureSlotAvailable(LocalDateTime scheduledAt, Integer durationMinutes, Long appointmentIdToIgnore) {
        LocalDateTime dayStart = scheduledAt.toLocalDate().atTime(OPENING_TIME);
        LocalDateTime dayEnd = scheduledAt.toLocalDate().atTime(CLOSING_TIME);
        List<Appointment> appointments = appointmentRepository.findByScheduledAtBetween(dayStart, dayEnd);
        
        LocalDateTime requestEnd = scheduledAt.plusMinutes(durationMinutes != null ? durationMinutes : 60);

        for (Appointment existing : appointments) {
            if (appointmentIdToIgnore != null && existing.getId().equals(appointmentIdToIgnore)) {
                continue;
            }
            if (existing.getStatus() == AppointmentStatus.CANCELED) {
                continue;
            }
            LocalDateTime existingStart = existing.getScheduledAt();
            LocalDateTime existingEnd = existingStart.plusMinutes(existing.getDurationMinutes());

            // Verifica sobreposição de horários
            if (scheduledAt.isBefore(existingEnd) && existingStart.isBefore(requestEnd)) {
                throw new BusinessException("Horário indisponível para agendamento");
            }
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
                appointment.getNotes(),
                appointment.getDurationMinutes(),
                appointment.getRecurrenceGroupId(),
                appointment.getRecurrencePeriod());
    }

    private int resolveDuration(AppointmentRequest request) {
        Integer candidate = request.durationMinutes();
        return candidate == null ? 60 : candidate;
    }
}