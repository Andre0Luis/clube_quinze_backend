package br.com.clube_quinze.api.service.user.impl;

import br.com.clube_quinze.api.dto.appointment.AppointmentResponse;
import br.com.clube_quinze.api.dto.payment.PlanSummary;
import br.com.clube_quinze.api.dto.preference.PreferenceResponse;
import br.com.clube_quinze.api.dto.user.UpdateUserRequest;
import br.com.clube_quinze.api.dto.user.UserGalleryPhotoRequest;
import br.com.clube_quinze.api.dto.user.UserGalleryPhotoResponse;
import br.com.clube_quinze.api.dto.user.UserProfileResponse;
import br.com.clube_quinze.api.dto.user.UserSummary;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.payment.Plan;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.model.user.UserGalleryPhoto;
import br.com.clube_quinze.api.model.user.UserPreference;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.PlanRepository;
import br.com.clube_quinze.api.repository.UserPreferenceRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.service.user.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final Clock clock;

    public UserServiceImpl(
            UserRepository userRepository,
            PlanRepository planRepository,
            AppointmentRepository appointmentRepository,
            UserPreferenceRepository userPreferenceRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.appointmentRepository = appointmentRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        return buildUserProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummary> listMembers(String planFilter) {
        String normalizedFilter = normalizeOptional(planFilter);
        List<User> users = normalizedFilter == null
                ? userRepository.findAllByOrderByNameAsc()
                : userRepository.findByPlan_NameContainingIgnoreCaseOrderByNameAsc(normalizedFilter);
        return users.stream()
                .map(this::toUserSummary)
                .toList();
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateUserRequest request) {
        User user = findUser(userId);

        if (!user.getEmail().equalsIgnoreCase(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email já cadastrado");
        }

        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setBirthDate(request.birthDate());
        user.setMembershipTier(request.membershipTier());

        Plan plan = null;
        if (request.planId() != null) {
            plan = planRepository.findById(request.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado"));
        }
        user.setPlan(plan);

        user.setProfilePictureUrl(normalizeOptional(request.profilePictureUrl()));
        user.setProfilePictureBase64(normalizeOptional(request.profilePictureBase64()));

        applyGallery(user, request.gallery());

        User updated = userRepository.save(user);
        return buildUserProfileResponse(updated);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private UserProfileResponse buildUserProfileResponse(User user) {
        PlanSummary planSummary = Optional.ofNullable(user.getPlan())
                .map(this::toPlanSummary)
                .orElse(null);

        AppointmentResponse nextAppointment = appointmentRepository.findUpcomingByClient(user.getId(), LocalDateTime.now(clock)).stream()
                .findFirst()
                .map(this::toAppointmentResponse)
                .orElse(null);

        List<PreferenceResponse> preferences = userPreferenceRepository.findByUserId(user.getId()).stream()
                .map(this::toPreferenceResponse)
                .toList();

        List<UserGalleryPhotoResponse> gallery = user.getGalleryPhotos().stream()
            .sorted(Comparator.comparing(UserGalleryPhoto::getPosition))
            .map(this::toGalleryPhotoResponse)
            .toList();

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getBirthDate(),
                user.getMembershipTier(),
                user.getRole(),
                planSummary,
                user.getCreatedAt(),
                user.getLastLogin(),
                nextAppointment,
            preferences,
            user.getProfilePictureUrl(),
            user.getProfilePictureBase64(),
            gallery);
    }

    private PlanSummary toPlanSummary(Plan plan) {
        return new PlanSummary(plan.getId(), plan.getName(), plan.getDescription(), plan.getPrice(), plan.getDurationMonths());
    }

    private UserSummary toUserSummary(User user) {
        PlanSummary planSummary = Optional.ofNullable(user.getPlan()).map(this::toPlanSummary).orElse(null);
        return new UserSummary(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getBirthDate(),
                user.getMembershipTier(),
                user.getRole(),
                user.getCreatedAt(),
                user.getLastLogin(),
                planSummary);
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

    private PreferenceResponse toPreferenceResponse(UserPreference preference) {
        return new PreferenceResponse(
                preference.getId(),
                preference.getPreferenceKey(),
                preference.getPreferenceValue(),
                preference.getCreatedAt(),
                preference.getUpdatedAt());
    }

    private UserGalleryPhotoResponse toGalleryPhotoResponse(UserGalleryPhoto photo) {
        return new UserGalleryPhotoResponse(
                photo.getId(),
                photo.getPosition(),
                photo.getImageUrl(),
                photo.getImageBase64());
    }

    private void applyGallery(User user, List<UserGalleryPhotoRequest> galleryRequests) {
        if (galleryRequests == null) {
            return;
        }

        user.getGalleryPhotos().clear();
        if (galleryRequests.isEmpty()) {
            return;
        }

        List<UserGalleryPhoto> photos = new ArrayList<>();
        Set<Integer> positions = new HashSet<>();
        int fallback = 0;

        for (UserGalleryPhotoRequest request : galleryRequests) {
            if (request == null) {
                continue;
            }

            String url = normalizeOptional(request.imageUrl());
            String base64 = normalizeOptional(request.imageBase64());

            if (url == null && base64 == null) {
                continue;
            }

            int position = request.position() != null ? request.position() : fallback;
            fallback++;

            if (position < 0 || position > 3) {
                throw new BusinessException("Posição da foto deve estar entre 0 e 3");
            }

            if (!positions.add(position)) {
                throw new BusinessException("Posição da foto duplicada na galeria");
            }

            UserGalleryPhoto photo = new UserGalleryPhoto();
            photo.setUser(user);
            photo.setPosition(position);
            photo.setImageUrl(url);
            photo.setImageBase64(base64);
            photos.add(photo);
        }

        if (photos.size() > 4) {
            throw new BusinessException("Limite máximo de 4 fotos na galeria");
        }

        photos.sort(Comparator.comparing(UserGalleryPhoto::getPosition));
        user.getGalleryPhotos().addAll(photos);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}