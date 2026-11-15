package br.com.clube_quinze.api.service.google;

import br.com.clube_quinze.api.config.AppConfig;
import br.com.clube_quinze.api.dto.google.ChooseCalendarRequest;
import br.com.clube_quinze.api.dto.google.GoogleCalendarOption;
import br.com.clube_quinze.api.dto.google.GoogleIntegrationStatus;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.model.enumeration.RoleType;
import br.com.clube_quinze.api.model.google.GoogleCalendarConnection;
import br.com.clube_quinze.api.model.google.GoogleCalendarConnection.Status;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.repository.GoogleCalendarConnectionRepository;
import br.com.clube_quinze.api.repository.UserRepository;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleAuthException;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleCalendarInfo;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleTokens;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conexão do administrador com o Google Agenda e sincronização dos agendamentos do clube:
 * conectar (OAuth), escolher/criar a agenda, criar/atualizar/remover eventos e desconectar.
 */
@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String ZONE = AppConfig.APP_ZONE.getId();

    private final GoogleProperties properties;
    private final GoogleCalendarGateway gateway;
    private final TokenCipher cipher;
    private final GoogleOAuthStateService stateService;
    private final GoogleCalendarConnectionRepository connectionRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final GoogleEventMapper eventMapper;
    // Transação programática: @Transactional não vale em chamada de dentro da própria classe (reconcile -> syncAppointment).
    private final TransactionTemplate tx;
    private final TransactionTemplate txNew;

    // Cache do access token (vale ~1h): evita pedir um novo ao Google a cada evento.
    private volatile String cachedAccessToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public GoogleCalendarService(GoogleProperties properties, GoogleCalendarGateway gateway, TokenCipher cipher,
                                 GoogleOAuthStateService stateService,
                                 GoogleCalendarConnectionRepository connectionRepository,
                                 AppointmentRepository appointmentRepository, UserRepository userRepository,
                                 GoogleEventMapper eventMapper, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.gateway = gateway;
        this.cipher = cipher;
        this.stateService = stateService;
        this.connectionRepository = connectionRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.eventMapper = eventMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoogleIntegrationStatus status() {
        if (!properties.isConfigured()) {
            return new GoogleIntegrationStatus(false, "NOT_CONFIGURED", null, null, null);
        }
        return connectionRepository.findFirstByOrderByIdAsc()
                .map(c -> new GoogleIntegrationStatus(true, c.getStatus().name(), c.getGoogleEmail(),
                        c.getCalendarId(), c.getCalendarSummary()))
                .orElse(new GoogleIntegrationStatus(true, "DISCONNECTED", null, null, null));
    }

    /** Há conta conectada e agenda definida? (consulta barata, usada antes de enfileirar uma sincronização) */
    @Transactional(readOnly = true)
    public boolean isSyncActive() {
        return properties.isConfigured()
                && connectionRepository.findFirstByOrderByIdAsc().map(GoogleCalendarConnection::isReady).orElse(false);
    }

    // ── Conectar / desconectar ────────────────────────────────────────────────

    public String startConnect(Long adminUserId) {
        requireConfigured();
        return gateway.buildAuthorizationUrl(stateService.create(adminUserId));
    }

    /** Conclui o OAuth: troca o código por tokens, guarda o refresh token criptografado e identifica a conta. */
    @Transactional
    public GoogleIntegrationStatus completeConnect(String code, String state) {
        requireConfigured();
        Long adminId = stateService.parse(state)
                .orElseThrow(() -> new BusinessException("Pedido de conexão inválido ou expirado. Tente conectar novamente pelo aplicativo."));
        boolean isAdmin = userRepository.findById(adminId).map(u -> u.getRole() == RoleType.CLUB_ADMIN).orElse(false);
        if (!isAdmin) {
            throw new BusinessException("Somente administradores podem conectar o Google Agenda.");
        }

        GoogleTokens tokens = gateway.exchangeCode(code);
        if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new BusinessException("O Google não devolveu a permissão de acesso contínuo. Remova o acesso do Clube Quinze "
                    + "em myaccount.google.com/permissions e conecte novamente.");
        }
        String email = gateway.listCalendars(tokens.accessToken()).stream()
                .filter(GoogleCalendarInfo::primary).map(GoogleCalendarInfo::id).findFirst().orElse(null);

        connectionRepository.deleteAll();
        connectionRepository.flush();
        GoogleCalendarConnection connection = new GoogleCalendarConnection();
        connection.setConnectedByUserId(adminId);
        connection.setGoogleEmail(email);
        connection.setRefreshTokenEnc(cipher.encrypt(tokens.refreshToken()));
        connection.setScopes(tokens.scope());
        connection.setStatus(Status.NEEDS_CALENDAR);
        connectionRepository.save(connection);

        cache(tokens);
        appointmentRepository.resetGoogleSync(); // conta nova: tudo precisa ser sincronizado de novo
        return status();
    }

    @Transactional
    public void disconnect() {
        Optional<GoogleCalendarConnection> existing = connectionRepository.findFirstByOrderByIdAsc();
        existing.ifPresent(c -> {
            try {
                gateway.revoke(cipher.decrypt(c.getRefreshTokenEnc()));
            } catch (RuntimeException ex) {
                log.warn("Não foi possível revogar o token no Google ao desconectar: {}", ex.getMessage());
            }
            connectionRepository.delete(c);
        });
        invalidateAccessTokenCache();
        appointmentRepository.resetGoogleSync();
    }

    // ── Escolha da agenda ─────────────────────────────────────────────────────

    @Transactional
    public List<GoogleCalendarOption> listCalendars() {
        GoogleCalendarConnection c = requireConnection();
        return gateway.listCalendars(accessToken(c)).stream()
                .map(i -> new GoogleCalendarOption(i.id(), i.summary(), i.primary()))
                .toList();
    }

    @Transactional
    public GoogleIntegrationStatus chooseCalendar(ChooseCalendarRequest request) {
        GoogleCalendarConnection c = requireConnection();
        String token = accessToken(c);
        GoogleCalendarInfo target;
        if ("CREATE".equals(request.mode())) {
            String name = request.name() != null && !request.name().isBlank() ? request.name().trim() : properties.getNewCalendarName();
            target = gateway.createCalendar(token, name, ZONE);
        } else {
            if (request.calendarId() == null || request.calendarId().isBlank()) {
                throw new BusinessException("Informe a agenda (calendarId).");
            }
            target = gateway.listCalendars(token).stream()
                    .filter(i -> i.id().equals(request.calendarId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Essa agenda não existe (ou não tem permissão de edição) na conta conectada."));
        }

        boolean changed = !target.id().equals(c.getCalendarId());
        c.setCalendarId(target.id());
        c.setCalendarSummary(target.summary());
        c.setStatus(Status.READY);
        connectionRepository.save(c);
        if (changed) {
            appointmentRepository.resetGoogleSync(); // agenda nova: recria os eventos nela
        }
        return status();
    }

    // ── Sincronização ─────────────────────────────────────────────────────────

    /** Reflete um agendamento no Google (cria/atualiza o evento; cancelado = remove). Sem agenda definida, não faz nada. */
    public void syncAppointment(Long appointmentId) {
        tx.executeWithoutResult(status -> doSync(appointmentId));
    }

    private void doSync(Long appointmentId) {
        Optional<GoogleCalendarConnection> connection = connectionRepository.findFirstByOrderByIdAsc();
        if (!properties.isConfigured() || connection.isEmpty() || !connection.get().isReady()) {
            return;
        }
        Appointment a = appointmentRepository.findById(appointmentId).orElse(null);
        if (a == null) {
            return;
        }
        LocalDateTime syncedFor = a.getUpdatedAt();
        String token = accessToken(connection.get());
        String calendarId = connection.get().getCalendarId();

        if (a.getStatus() == AppointmentStatus.CANCELED) {
            if (a.getGoogleEventId() != null) {
                gateway.deleteEvent(token, calendarId, a.getGoogleEventId());
            }
            appointmentRepository.markGoogleSynced(a.getId(), null, syncedFor);
            return;
        }
        String eventId = gateway.upsertEvent(token, calendarId, a.getGoogleEventId(), eventMapper.toEvent(a));
        appointmentRepository.markGoogleSynced(a.getId(), eventId, syncedFor);
    }

    /** Sincroniza o que ainda não está refletido no Google (rede de segurança e carga inicial). Devolve quantos foram. */
    public int reconcile(int max) {
        if (!isSyncActive()) {
            return 0;
        }
        List<Appointment> pending = appointmentRepository.findNeedingGoogleSync(
                LocalDateTime.now(AppConfig.APP_ZONE).minusDays(1), PageRequest.of(0, max));
        int done = 0;
        for (Appointment a : pending) {
            try {
                syncAppointment(a.getId());
                done++;
            } catch (GoogleAuthException ex) {
                log.warn("Google Agenda: acesso recusado, sincronização interrompida ({})", ex.getMessage());
                break;
            } catch (RuntimeException ex) {
                log.warn("Google Agenda: falha ao sincronizar o agendamento {}: {}", a.getId(), ex.getMessage());
            }
        }
        return done;
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    private GoogleCalendarConnection requireConnection() {
        requireConfigured();
        GoogleCalendarConnection c = connectionRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new BusinessException("Nenhuma conta do Google conectada."));
        if (c.getStatus() == Status.REVOKED) {
            throw new BusinessException("O acesso à conta do Google foi revogado. Conecte novamente.");
        }
        return c;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException("A integração com o Google Agenda não está configurada neste servidor.");
        }
    }

    /** Access token válido: reaproveita o do cache ou troca o refresh token por um novo. */
    private String accessToken(GoogleCalendarConnection connection) {
        if (cachedAccessToken != null && Instant.now().isBefore(cachedExpiry.minusSeconds(60))) {
            return cachedAccessToken;
        }
        try {
            GoogleTokens tokens = gateway.refresh(cipher.decrypt(connection.getRefreshTokenEnc()));
            cache(tokens);
            return tokens.accessToken();
        } catch (GoogleAuthException ex) {
            // Acesso revogado/expirado no Google: marca para o admin reconectar e para de tentar. Em transação própria,
            // porque a exceção abaixo reverte a transação da sincronização e apagaria a marcação.
            Long connectionId = connection.getId();
            txNew.executeWithoutResult(status -> connectionRepository.findById(connectionId).ifPresent(c -> {
                c.setStatus(Status.REVOKED);
                connectionRepository.save(c);
            }));
            invalidateAccessTokenCache();
            throw ex;
        }
    }

    /** Esquece o access token em memória (próxima chamada pede um novo ao Google). */
    public void invalidateAccessTokenCache() {
        cachedAccessToken = null;
        cachedExpiry = Instant.EPOCH;
    }

    private void cache(GoogleTokens tokens) {
        cachedAccessToken = tokens.accessToken();
        cachedExpiry = Instant.now().plusSeconds(tokens.expiresInSeconds());
    }
}
