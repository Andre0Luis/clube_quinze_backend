package br.com.clube_quinze.api.service.appointment;

import br.com.clube_quinze.api.config.AppConfig;
import br.com.clube_quinze.api.exception.ResourceNotFoundException;
import br.com.clube_quinze.api.exception.UnauthorizedException;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convites de calendário de um agendamento: link "Adicionar ao Google Agenda" e arquivo .ics (Apple, Outlook, etc.).
 * Os horários de agendamento são relógio de Brasília; os convites saem em UTC para funcionar em qualquer fuso.
 */
@Service
public class AppointmentCalendarService {

    private static final DateTimeFormatter ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String CRLF = "\r\n";

    private final AppointmentRepository appointmentRepository;
    private final AppointmentActionTokenService tokenService;
    private final String publicBaseUrl;
    private final String clubName;
    private final String clubLocation;

    public AppointmentCalendarService(AppointmentRepository appointmentRepository,
                                      AppointmentActionTokenService tokenService,
                                      @Value("${app.public.base-url}") String publicBaseUrl,
                                      @Value("${app.club.name:Clube Quinze}") String clubName,
                                      @Value("${app.club.location:}") String clubLocation) {
        this.appointmentRepository = appointmentRepository;
        this.tokenService = tokenService;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        this.clubName = clubName;
        this.clubLocation = clubLocation == null ? "" : clubLocation;
    }

    /** Agendamento do próprio usuário (ou de qualquer um, para admin/funcionário), já validado. */
    @Transactional(readOnly = true)
    public Appointment findForUser(Long appointmentId, Long actorId, boolean privileged) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        if (!privileged && !appointment.getClient().getId().equals(actorId)) {
            throw new UnauthorizedException("Não é permitido acessar este agendamento");
        }
        return appointment;
    }

    @Transactional(readOnly = true)
    public Appointment findById(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
    }

    /** Link do Google Agenda com o evento já preenchido. */
    public String googleCalendarUrl(Appointment a) {
        Instant start = startOf(a);
        Instant end = start.plusSeconds(a.getDurationMinutes() * 60L);
        StringBuilder url = new StringBuilder("https://calendar.google.com/calendar/render?action=TEMPLATE");
        url.append("&text=").append(enc(title(a)));
        url.append("&dates=").append(ICS_UTC.format(start)).append('/').append(ICS_UTC.format(end));
        url.append("&details=").append(enc(description(a)));
        if (!clubLocation.isBlank()) {
            url.append("&location=").append(enc(clubLocation));
        }
        return url.toString();
    }

    /** Link público (assinado) para baixar o .ics; vale até um dia depois do atendimento. */
    public String icsUrl(Appointment a) {
        Instant expiresAt = startOf(a).plusSeconds(24 * 3600L);
        return publicBaseUrl + "/appointment-calendar?token=" + tokenService.create(a.getId(), Action.CALENDAR, expiresAt);
    }

    /** Conteúdo do arquivo .ics (RFC 5545), com quebras de linha CRLF e linhas dobradas em 75 bytes. */
    public String buildIcs(Appointment a) {
        Instant start = startOf(a);
        Instant end = start.plusSeconds(a.getDurationMinutes() * 60L);
        String host = hostOf(publicBaseUrl);
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//" + clubName + "//Agendamentos//PT-BR");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:appointment-" + a.getId() + "@" + host);
        line(ics, "DTSTAMP:" + ICS_UTC.format(Instant.now()));
        line(ics, "DTSTART:" + ICS_UTC.format(start));
        line(ics, "DTEND:" + ICS_UTC.format(end));
        line(ics, "SUMMARY:" + escape(title(a)));
        line(ics, "DESCRIPTION:" + escape(description(a)));
        if (!clubLocation.isBlank()) {
            line(ics, "LOCATION:" + escape(clubLocation));
        }
        line(ics, "STATUS:" + (a.getStatus() == AppointmentStatus.CANCELED ? "CANCELLED" : "CONFIRMED"));
        line(ics, "BEGIN:VALARM");
        line(ics, "TRIGGER:-PT60M");
        line(ics, "ACTION:DISPLAY");
        line(ics, "DESCRIPTION:" + escape("Atendimento em 1 hora — " + clubName));
        line(ics, "END:VALARM");
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private Instant startOf(Appointment a) {
        return a.getScheduledAt().atZone(AppConfig.APP_ZONE).toInstant();
    }

    private String title(Appointment a) {
        String service = a.getServiceType();
        return service != null && !service.isBlank() ? clubName + " — " + service.trim() : "Atendimento — " + clubName;
    }

    private String description(Appointment a) {
        String notes = a.getNotes() == null ? "" : a.getNotes().trim();
        return notes.isEmpty() ? "Agendamento no " + clubName : notes + "\n\nAgendamento no " + clubName;
    }

    private static String hostOf(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return host != null ? host : "clube-quinze";
        } catch (IllegalArgumentException ex) {
            return "clube-quinze";
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Escapa texto conforme a RFC 5545 (\, ;, vírgula e quebra de linha). */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    private static void line(StringBuilder out, String content) {
        out.append(fold(content)).append(CRLF);
    }

    /** Dobra a linha em até 75 bytes UTF-8 (continuação começa com um espaço), sem partir caracteres. */
    static String fold(String content) {
        byte[] all = content.getBytes(StandardCharsets.UTF_8);
        if (all.length <= 75) {
            return content;
        }
        StringBuilder out = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        int limit = 75;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes + size > limit) {
                out.append(current).append(CRLF).append(' ');
                current.setLength(0);
                currentBytes = 0;
                limit = 74; // o espaço da continuação conta no limite
            }
            current.append(ch);
            currentBytes += size;
            i += Character.charCount(cp);
        }
        return out.append(current).toString();
    }
}
