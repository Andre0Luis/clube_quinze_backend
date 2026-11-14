package br.com.clube_quinze.api.service.appointment.impl;

import br.com.clube_quinze.api.dto.appointment.AppointmentRequest;
import br.com.clube_quinze.api.dto.appointment.AppointmentRescheduleApprovalRequest;
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
import br.com.clube_quinze.api.model.enumeration.RoleType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
    private static final int GRID_MINUTES = AppointmentScheduleSettings.GRID_MINUTES;
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
    public AvailableSlotResponse getAvailableSlots(LocalDate date, MembershipTier tier, Integer durationMinutes) {
        MembershipTier effectiveTier = tier == null ? MembershipTier.QUINZE_STANDARD : tier;
        if (durationMinutes != null) {
            validateDuration(durationMinutes);
        }
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
                    curr = curr.plusMinutes(GRID_MINUTES);
                }
            }
        }

        // Com duração customizada (admin) os inícios seguem a grade de 15 min; sem ela, mantém a grade
        // do plano (Select: blocos de 2h; demais: 30 min) para não mudar a experiência dos clientes.
        Duration durationNeeded = durationMinutes != null
                ? Duration.ofMinutes(durationMinutes)
                : defaultDurationFor(effectiveTier);
        Duration step = durationMinutes != null
                ? Duration.ofMinutes(GRID_MINUTES)
                : defaultStepFor(effectiveTier);

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
            curr = curr.plusMinutes(GRID_MINUTES);
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

        int duration = resolveDuration(request, privileged);
        validateBusinessHours(request.scheduledAt(), duration);
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
            String when = saved.getScheduledAt().format(PT_BR_FORMATTER);
            var data = new java.util.HashMap<String, Object>();
            data.put("appointmentId", saved.getId());
            data.put("scheduledAt", saved.getScheduledAt().toString());

            // Cliente: confirmação do próprio agendamento
            var clientPush = new java.util.HashMap<String, Object>();
            clientPush.put("userId", saved.getClient().getId());
            clientPush.put("type", "SCHEDULED");
            clientPush.put("title", "Agendamento marcado");
            clientPush.put("body", "Seu agendamento foi criado para " + when);
            clientPush.put("data", data);
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("PUSH_MESSAGE", clientPush));

            // Admins: aviso de novo agendamento COM o nome do cliente que será atendido
            String clientName = saved.getClient() != null && saved.getClient().getName() != null
                    ? saved.getClient().getName()
                    : "Cliente";
            for (User admin : userRepository.findByRole(RoleType.CLUB_ADMIN)) {
                var adminPush = new java.util.HashMap<String, Object>();
                adminPush.put("userId", admin.getId());
                adminPush.put("type", "ADMIN_NEW_APPOINTMENT");
                adminPush.put("title", "Novo agendamento");
                adminPush.put("body", clientName + " agendou para " + when);
                adminPush.put("data", data);
                rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, new NotificationMessageDTO("PUSH_MESSAGE", adminPush));
            }
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
        if (!privileged) {
            // Cliente nunca remarca direto: vira uma proposta que o admin precisa aprovar.
            return requestReschedule(appointmentId, actorId, request);
        }
        Appointment appointment = findAppointment(appointmentId);

        // capture old time before update
        LocalDateTime oldScheduledAt = appointment.getScheduledAt();

        validateAppointmentDate(request.newDate());

        // Só admin/funcionário pode alterar a duração; cliente mantém a do agendamento.
        int duration = privileged && request.durationMinutes() != null
                ? validateDuration(request.durationMinutes())
                : appointment.getDurationMinutes();
        validateBusinessHours(request.newDate(), duration);
        ensureSlotAvailable(request.newDate(), duration, appointment.getId());

        appointment.setScheduledAt(request.newDate());
        appointment.setDurationMinutes(duration);
        if (request.notes() != null && !request.notes().isBlank()) {
            appointment.setNotes(request.notes());
        }
        // Novo horário definido pelo clube: volta a "agendado" (o cliente confirma de novo) e qualquer proposta cai.
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setConfirmedAt(null);
        appointment.setProposedAt(null);

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
        if (request.status() == AppointmentStatus.RESCHEDULE_REQUESTED) {
            throw new BusinessException("Remarcações são solicitadas pelo cliente; use aprovar/recusar");
        }
        appointment.setStatus(request.status());
        if (request.status() == AppointmentStatus.CONFIRMED && appointment.getConfirmedAt() == null) {
            appointment.setConfirmedAt(LocalDateTime.now(clock));
        } else if (request.status() == AppointmentStatus.SCHEDULED) {
            appointment.setConfirmedAt(null);
        }
        appointment.setProposedAt(null);
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
        cancelInternal(appointment, !privileged);
    }

    @Override
    @Transactional
    public AppointmentResponse cancelViaLink(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        return toAppointmentResponse(cancelInternal(appointment, true));
    }

    private Appointment cancelInternal(Appointment appointment, boolean byClient) {
        if (appointment.getStatus() == AppointmentStatus.CANCELED) {
            return appointment; // idempotente
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BusinessException("Atendimento já concluído não pode ser cancelado");
        }
        appointment.setStatus(AppointmentStatus.CANCELED);
        appointment.setProposedAt(null);
        Appointment saved = appointmentRepository.save(appointment);

        String when = saved.getScheduledAt().format(PT_BR_FORMATTER);
        Map<String, Object> data = appointmentData(saved, "appointment_canceled");
        if (byClient) {
            pushToUser(saved.getClient().getId(), "CANCELLED", "Agendamento cancelado",
                    "Você cancelou seu agendamento de " + when + ".", data);
            pushToAdmins("ADMIN_APPOINTMENT_CANCELED", "Cliente cancelou",
                    clientName(saved) + " cancelou o atendimento de " + when + ".",
                    appointmentData(saved, "admin_appointment_canceled"));
        } else {
            pushToUser(saved.getClient().getId(), "CANCELLED", "Agendamento cancelado",
                    "Seu agendamento foi cancelado pelo clube.", data);
        }
        return saved;
    }

    // ── Confirmação / proposta de remarcação ──────────────────────────────────

    @Override
    @Transactional
    public AppointmentResponse confirm(Long appointmentId, Long actorId, boolean privileged) {
        Appointment appointment = findAppointment(appointmentId);
        enforceOwnership(appointment, actorId, privileged);
        return toAppointmentResponse(confirmInternal(appointment, !privileged));
    }

    @Override
    @Transactional
    public AppointmentResponse confirmViaLink(Long appointmentId) {
        return toAppointmentResponse(confirmInternal(findAppointment(appointmentId), true));
    }

    private Appointment confirmInternal(Appointment appointment, boolean byClient) {
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            return appointment; // idempotente
        }
        if (appointment.getStatus() == AppointmentStatus.RESCHEDULE_REQUESTED) {
            throw new BusinessException(byClient
                    ? "Há um pedido de remarcação aguardando a resposta do clube"
                    : "Há um pedido de remarcação pendente: aprove ou recuse");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new BusinessException("Este agendamento não pode mais ser confirmado");
        }
        if (!appointment.getScheduledAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("Este agendamento já passou");
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(LocalDateTime.now(clock));
        Appointment saved = appointmentRepository.save(appointment);

        String when = saved.getScheduledAt().format(PT_BR_FORMATTER);
        if (byClient) {
            pushToAdmins("ADMIN_APPOINTMENT_CONFIRMED", "Atendimento confirmado",
                    clientName(saved) + " confirmou o atendimento de " + when + ".",
                    appointmentData(saved, "admin_appointment_confirmed"));
        } else {
            pushToUser(saved.getClient().getId(), "CONFIRMED", "Agendamento mantido",
                    "O clube manteve seu agendamento de " + when + ".",
                    appointmentData(saved, "appointment_confirmed"));
        }
        return saved;
    }

    @Override
    @Transactional
    public AppointmentResponse requestReschedule(Long appointmentId, Long actorId, AppointmentRescheduleRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        enforceOwnership(appointment, actorId, false);
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED
                && appointment.getStatus() != AppointmentStatus.CONFIRMED
                && appointment.getStatus() != AppointmentStatus.RESCHEDULE_REQUESTED) {
            throw new BusinessException("Este agendamento não pode ser remarcado");
        }
        validateAppointmentDate(request.newDate());
        // O cliente nunca altera a duração: valida o expediente com a duração atual do agendamento.
        validateBusinessHours(request.newDate(), appointment.getDurationMinutes());
        if (request.newDate().equals(appointment.getScheduledAt())) {
            throw new BusinessException("O novo horário é igual ao horário atual");
        }

        // O horário proposto NÃO reserva a agenda; se já houver conflito, o admin é avisado e decide.
        Optional<Appointment> conflict =
                findConflict(request.newDate(), appointment.getDurationMinutes(), appointment.getId());

        LocalDateTime oldAt = appointment.getScheduledAt();
        appointment.setStatus(AppointmentStatus.RESCHEDULE_REQUESTED);
        appointment.setProposedAt(request.newDate());
        Appointment saved = appointmentRepository.save(appointment);

        String oldFmt = oldAt.format(PT_BR_FORMATTER);
        String newFmt = request.newDate().format(PT_BR_FORMATTER);
        StringBuilder body = new StringBuilder(clientName(saved))
                .append(" pediu para remarcar de ").append(oldFmt).append(" para ").append(newFmt).append(".");
        if (request.notes() != null && !request.notes().isBlank()) {
            body.append(" Motivo: ").append(request.notes().trim());
        }
        conflict.ifPresent(c -> body.append(" ⚠ Conflito: o horário pedido já está ocupado por ")
                .append(clientName(c)).append(" (").append(describeRange(c)).append(").")
                .append(" Aprove em outro horário ou recuse."));
        Map<String, Object> adminData = appointmentData(saved, "admin_reschedule_request");
        adminData.put("proposedAt", request.newDate().toString());
        adminData.put("conflict", conflict.isPresent());
        pushToAdmins("ADMIN_RESCHEDULE_REQUEST", "Pedido de remarcação", body.toString(), adminData);

        pushToUser(saved.getClient().getId(), "RESCHEDULE_REQUESTED", "Pedido de remarcação enviado",
                "Pedimos a remarcação para " + newFmt + ". Aguarde a aprovação do clube; "
                        + "enquanto isso seu horário de " + oldFmt + " continua reservado.",
                appointmentData(saved, "appointment_reschedule_requested"));
        return toAppointmentResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse approveReschedule(Long appointmentId, AppointmentRescheduleApprovalRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_REQUESTED || appointment.getProposedAt() == null) {
            throw new BusinessException("Não há pedido de remarcação pendente para este agendamento");
        }
        LocalDateTime target = request != null && request.newDate() != null
                ? request.newDate()
                : appointment.getProposedAt();
        int duration = request != null && request.durationMinutes() != null
                ? validateDuration(request.durationMinutes())
                : appointment.getDurationMinutes();

        validateAppointmentDate(target);
        validateBusinessHours(target, duration);
        Optional<Appointment> conflict = findConflict(target, duration, appointment.getId());
        if (conflict.isPresent()) {
            Appointment c = conflict.get();
            throw new BusinessException("Conflito: " + target.format(PT_BR_FORMATTER) + " já está ocupado por "
                    + clientName(c) + " (" + describeRange(c) + "). Aprove em outro horário (informe newDate) "
                    + "ou recuse o pedido.");
        }

        LocalDateTime oldAt = appointment.getScheduledAt();
        appointment.setScheduledAt(target);
        appointment.setDurationMinutes(duration);
        appointment.setProposedAt(null);
        // Cliente propôs e o clube aprovou: as duas partes concordam, então já fica confirmado.
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(LocalDateTime.now(clock));
        Appointment saved = appointmentRepository.save(appointment);

        String newFmt = target.format(PT_BR_FORMATTER);
        pushToUser(saved.getClient().getId(), "RESCHEDULED", "Remarcação aprovada",
                "Seu agendamento foi remarcado para " + newFmt + ".",
                appointmentData(saved, "appointment_rescheduled"));
        enqueueRescheduledEmail(saved, oldAt);
        return toAppointmentResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse rejectReschedule(Long appointmentId, String reason) {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_REQUESTED) {
            throw new BusinessException("Não há pedido de remarcação pendente para este agendamento");
        }
        appointment.setProposedAt(null);
        appointment.setStatus(appointment.getConfirmedAt() != null
                ? AppointmentStatus.CONFIRMED
                : AppointmentStatus.SCHEDULED);
        Appointment saved = appointmentRepository.save(appointment);

        StringBuilder body = new StringBuilder("O clube não pôde remarcar. Seu agendamento segue em ")
                .append(saved.getScheduledAt().format(PT_BR_FORMATTER)).append(".");
        if (reason != null && !reason.isBlank()) {
            body.append(" Motivo: ").append(reason.trim());
        }
        pushToUser(saved.getClient().getId(), "RESCHEDULE_REJECTED", "Remarcação não aprovada",
                body.toString(), appointmentData(saved, "appointment_reschedule_rejected"));
        return toAppointmentResponse(saved);
    }

    private void enqueueRescheduledEmail(Appointment updated, LocalDateTime oldAt) {
        try {
            User client = updated.getClient();
            var emailMap = new HashMap<String, Object>();
            emailMap.put("email", client.getEmail());
            emailMap.put("name", client.getName());
            emailMap.put("oldScheduledAt", oldAt.format(PT_BR_FORMATTER));
            emailMap.put("newScheduledAt", updated.getScheduledAt().format(PT_BR_FORMATTER));
            emailMap.put("description", updated.getNotes() != null ? updated.getNotes() : "");
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    new NotificationMessageDTO("APPOINTMENT_RESCHEDULED_EMAIL", emailMap));
        } catch (Exception ignored) {
            // best-effort
        }
    }

    // ── helpers de notificação (best-effort: nunca quebram o fluxo principal) ──

    private Map<String, Object> appointmentData(Appointment a, String kind) {
        Map<String, Object> data = new HashMap<>();
        data.put("appointmentId", a.getId());
        data.put("scheduledAt", a.getScheduledAt().toString());
        data.put("kind", kind);
        return data;
    }

    private void pushToUser(Long userId, String type, String title, String body, Map<String, Object> data) {
        try {
            var push = new HashMap<String, Object>();
            push.put("userId", userId);
            push.put("type", type);
            push.put("title", title);
            push.put("body", body);
            push.put("data", data);
            rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    new NotificationMessageDTO("PUSH_MESSAGE", push));
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void pushToAdmins(String type, String title, String body, Map<String, Object> data) {
        try {
            for (User admin : userRepository.findByRole(RoleType.CLUB_ADMIN)) {
                pushToUser(admin.getId(), type, title, body, data);
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private String clientName(Appointment a) {
        return a.getClient() != null && a.getClient().getName() != null ? a.getClient().getName() : "Cliente";
    }

    private String describeRange(Appointment a) {
        return a.getScheduledAt().toLocalTime() + "–"
                + a.getScheduledAt().plusMinutes(a.getDurationMinutes()).toLocalTime();
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
        if (findConflict(scheduledAt, durationMinutes, appointmentIdToIgnore).isPresent()) {
            throw new BusinessException("Horário indisponível para agendamento");
        }
    }

    /** Primeiro atendimento ativo que se sobrepõe ao intervalo pedido (ignora cancelados e o próprio agendamento). */
    private Optional<Appointment> findConflict(LocalDateTime scheduledAt, Integer durationMinutes, Long appointmentIdToIgnore) {
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
                return Optional.of(existing);
            }
        }
        return Optional.empty();
    }

    private void validateAppointmentDate(LocalDateTime scheduledAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!scheduledAt.isAfter(now)) {
            throw new BusinessException("O agendamento deve ser em uma data futura");
        }
    }

    private void validateBusinessHours(LocalDateTime scheduledAt, int durationMinutes) {
        LocalTime time = scheduledAt.toLocalTime();
        if (time.getMinute() % GRID_MINUTES != 0 || time.getSecond() != 0) {
            throw new BusinessException("O horário deve começar em múltiplos de 15 minutos");
        }
        LocalTime lastStart = CLOSING_TIME.minusMinutes(durationMinutes);
        if (time.isBefore(OPENING_TIME) || time.isAfter(lastStart)) {
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
                appointment.getRecurrencePeriod(),
                appointment.getConfirmedAt(),
                appointment.getProposedAt());
    }

    /**
     * Admin/funcionário define a duração (padrão 30 min). Cliente agenda sempre com a duração padrão do
     * plano (Select 2h, demais 30 min); o admin ajusta depois, ao aprovar/editar.
     */
    private int resolveDuration(AppointmentRequest request, boolean privileged) {
        if (!privileged) {
            return (int) defaultDurationFor(request.appointmentTier()).toMinutes();
        }
        Integer candidate = request.durationMinutes();
        return candidate == null
                ? AppointmentScheduleSettings.DEFAULT_DURATION_MINUTES
                : validateDuration(candidate);
    }

    private int validateDuration(int minutes) {
        if (minutes < AppointmentScheduleSettings.MIN_DURATION_MINUTES
                || minutes > AppointmentScheduleSettings.MAX_DURATION_MINUTES) {
            throw new BusinessException("A duração deve estar entre "
                    + AppointmentScheduleSettings.MIN_DURATION_MINUTES + " e "
                    + AppointmentScheduleSettings.MAX_DURATION_MINUTES + " minutos");
        }
        if (minutes % GRID_MINUTES != 0) {
            throw new BusinessException("A duração deve ser múltipla de 15 minutos");
        }
        return minutes;
    }

    private Duration defaultDurationFor(MembershipTier tier) {
        return tier == MembershipTier.QUINZE_SELECT
                ? Duration.ofMinutes(AppointmentScheduleSettings.SELECT_DURATION_MINUTES)
                : Duration.ofMinutes(AppointmentScheduleSettings.DEFAULT_DURATION_MINUTES);
    }

    private Duration defaultStepFor(MembershipTier tier) {
        return tier == MembershipTier.QUINZE_SELECT
                ? Duration.ofMinutes(AppointmentScheduleSettings.SELECT_DURATION_MINUTES)
                : SLOT_DURATION;
    }
}