package br.com.clube_quinze.api.service.feedback.impl;

import br.com.clube_quinze.api.dto.common.PageResponse;
import br.com.clube_quinze.api.dto.feedback.FeedbackAverageResponse;
import br.com.clube_quinze.api.dto.feedback.FeedbackRequest;
import br.com.clube_quinze.api.dto.feedback.FeedbackResponse;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.feedback.Feedback;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.FeedbackRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.service.feedback.FeedbackService;
import br.com.clube_quinze.api.service.notification.NotificationService;
import br.com.clube_quinze.api.util.PageUtils;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FeedbackServiceImpl(
            FeedbackRepository feedbackRepository,
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.feedbackRepository = feedbackRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public FeedbackResponse submitFeedback(Long actorId, boolean privileged, FeedbackRequest request) {
        Appointment appointment = findAppointment(request.appointmentId());
        User client = appointment.getClient();

        if (!privileged && !client.getId().equals(actorId)) {
            throw new UnauthorizedException("Não é permitido avaliar este atendimento");
        }

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessException("Somente atendimentos concluídos podem receber feedback");
        }

        feedbackRepository.findByAppointmentIdAndUserId(appointment.getId(), client.getId()).ifPresent(existing -> {
            throw new BusinessException("Já existe um feedback para este atendimento");
        });

        Feedback feedback = new Feedback();
        feedback.setAppointment(appointment);
        feedback.setUser(client);
        feedback.setRating(request.rating());
        feedback.setComment(request.comment());

        Feedback saved = feedbackRepository.save(feedback);

        // Notificar de forma assíncrona sem impactar a resposta
        notificationService.notifyFeedbackReceived(
            client.getId(),
            appointment.getId(),
            request.rating());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> getMyFeedback(Long actorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FeedbackResponse> result = feedbackRepository.findByUserId(actorId, pageable)
                .map(this::toResponse);
        return PageUtils.toResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> getFeedback(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FeedbackResponse> pageResult;
        if (userId != null) {
            ensureUserExists(userId);
            pageResult = feedbackRepository.findByUserId(userId, pageable).map(this::toResponse);
        } else {
            pageResult = feedbackRepository.findAll(pageable).map(this::toResponse);
        }
        return PageUtils.toResponse(pageResult);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackAverageResponse> getAverageByService() {
        return feedbackRepository.findAverageRatingByService().stream()
                .map(view -> new FeedbackAverageResponse(view.getService(), view.getAverage()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Double getUserAverage(Long userId) {
        ensureUserExists(userId);
        return feedbackRepository.findAverageRatingForUser(userId);
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
    }

    private FeedbackResponse toResponse(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getAppointment().getId(),
                feedback.getUser().getId(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.getCreatedAt());
    }
}