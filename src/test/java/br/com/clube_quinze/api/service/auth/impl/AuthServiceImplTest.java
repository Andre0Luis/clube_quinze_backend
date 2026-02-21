package br.com.clube_quinze.api.service.auth.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.clube_quinze.api.dto.auth.ForgotPasswordRequest;
import br.com.clube_quinze.api.dto.auth.ResetPasswordRequest;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.model.user.PasswordResetToken;
import br.com.clube_quinze.api.model.user.User;
import br.com.clube_quinze.api.repository.PasswordResetTokenRepository;
import br.com.clube_quinze.api.repository.PlanRepository;
import br.com.clube_quinze.api.repository.RefreshTokenRepository;
import br.com.clube_quinze.api.repository.UserPreferenceRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.security.JwtProperties;
import br.com.clube_quinze.api.security.JwtTokenProvider;
import br.com.clube_quinze.api.service.notification.NotificationService;
import br.com.clube_quinze.api.service.appointment.RecurringAppointmentScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private RecurringAppointmentScheduler recurringAppointmentScheduler;

    private Clock clock;
    private AuthServiceImpl subject;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-02-20T10:00:00Z"), ZoneId.of("UTC"));
        subject = new AuthServiceImpl(
                authenticationManager,
                userRepository,
                planRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                jwtProperties,
                clock,
                notificationService,
            userPreferenceRepository,
            recurringAppointmentScheduler,
                30,
                "https://clubequinzeapp.cloud/reset-password"
        );
    }

    @Test
    void requestPasswordReset_generatesTokenAndSendsEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("André");
        user.setActive(true);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        subject.requestPasswordReset(new ForgotPasswordRequest("user@example.com"));

        verify(passwordResetTokenRepository).deleteByUserId(1L);
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertNotNull(savedToken.getToken());
        assertEquals(user, savedToken.getUser());
        assertEquals(clock.instant().plusSeconds(30 * 60), savedToken.getExpiresAt());

        verify(notificationService).notifyPasswordReset(eq("user@example.com"), eq("André"), anyString());
    }

    @Test
    void requestPasswordReset_doesNothingIfUserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        subject.requestPasswordReset(new ForgotPasswordRequest("notfound@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(notificationService, never()).notifyPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_updatesPasswordAndMarksTokenUsed() {
        User user = new User();
        user.setId(1L);
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setExpiresAt(clock.instant().plusSeconds(600));

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("hashed-password");

        subject.resetPassword(new ResetPasswordRequest("valid-token", "new-password"));

        assertEquals("hashed-password", user.getPasswordHash());
        assertNotNull(token.getUsedAt());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void resetPassword_throwsIfTokenExpired() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired-token");
        token.setExpiresAt(clock.instant().minusSeconds(1));

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThrows(BusinessException.class, () -> 
            subject.resetPassword(new ResetPasswordRequest("expired-token", "new-password"))
        );
    }

    @Test
    void resetPassword_throwsIfTokenAlreadyUsed() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("used-token");
        token.setUsedAt(clock.instant());
        token.setExpiresAt(clock.instant().plusSeconds(600));

        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        assertThrows(BusinessException.class, () -> 
            subject.resetPassword(new ResetPasswordRequest("used-token", "new-password"))
        );
    }
}
