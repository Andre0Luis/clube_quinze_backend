package br.com.clube_quinze.api.service.auth.impl;

import br.com.clube_quinze.api.dto.auth.AuthResponse;
import br.com.clube_quinze.api.dto.auth.LoginRequest;
import br.com.clube_quinze.api.dto.auth.RefreshTokenRequest;
import br.com.clube_quinze.api.dto.auth.RegisterRequest;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.payment.Plan;
import br.com.clube_quinze.api.model.user.RefreshToken;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.PlanRepository;
import br.com.clube_quinze.api.repository.RefreshTokenRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.security.JwtProperties;
import br.com.clube_quinze.api.security.JwtTokenProvider;
import br.com.clube_quinze.api.service.auth.AuthService;
import br.com.clube_quinze.api.service.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final NotificationService notificationService;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            PlanRepository planRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            Clock clock,
            NotificationService notificationService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email já cadastrado");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setBirthDate(request.birthDate());
    user.setMembershipTier(request.membershipTier());
    user.setRole(RoleType.CLUB_STANDARD);

        if (request.planId() != null) {
            Plan plan = planRepository.findById(request.planId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado"));
            user.setPlan(plan);
        }

        User savedUser = userRepository.save(user);

        // Envio de boas-vindas com credenciais (assíncrono)
        notificationService.notifyWelcome(savedUser.getEmail(), savedUser.getName(), request.password());

        return issueTokensFor(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(request.email(), request.password());
            authenticationManager.authenticate(authenticationToken);
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Credenciais inválidas");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Usuário inativo");
        }

        user.setLastLogin(LocalDateTime.now(clock));
        User updatedUser = userRepository.save(user);
        return issueTokensFor(updatedUser);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken existingToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));

        if (existingToken.isRevoked() || existingToken.getExpiresAt().isBefore(clock.instant())) {
            throw new UnauthorizedException("Refresh token expirado ou revogado");
        }

        if (!jwtTokenProvider.isTokenValid(request.refreshToken())) {
            throw new UnauthorizedException("Refresh token inválido");
        }

        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);

        User user = existingToken.getUser();
        return issueTokensFor(user);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        Optional<RefreshToken> maybeToken = refreshTokenRepository.findByToken(request.refreshToken());
        maybeToken.ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse issueTokensFor(User user) {
        refreshTokenRepository.deleteByUserId(user.getId());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshTokenValue = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(calculateRefreshExpiration());
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.bearer(accessToken, refreshTokenValue);
    }

    private Instant calculateRefreshExpiration() {
        return clock.instant().plus(jwtProperties.refreshTokenTtl());
    }
}