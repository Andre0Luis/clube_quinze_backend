package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import br.com.clube_quinze.api.service.appointment.AppointmentCalendarService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Download público do .ics a partir do link assinado enviado por e-mail (aberto direto no navegador/celular).
 * O token (HMAC, expira um dia depois do atendimento) é a única credencial; nada além do evento é exposto.
 */
@RestController
public class AppointmentCalendarPageController {

    private final AppointmentActionTokenService tokenService;
    private final AppointmentCalendarService calendarService;

    public AppointmentCalendarPageController(AppointmentActionTokenService tokenService,
                                             AppointmentCalendarService calendarService) {
        this.tokenService = tokenService;
        this.calendarService = calendarService;
    }

    @GetMapping("/appointment-calendar")
    public ResponseEntity<String> download(@RequestParam("token") String token) {
        var parsed = tokenService.parse(token);
        if (parsed.isEmpty() || parsed.get().action() != Action.CALENDAR) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Link inválido ou expirado.");
        }
        Appointment appointment = calendarService.findById(parsed.get().appointmentId());
        return icsResponse(appointment, calendarService.buildIcs(appointment));
    }

    static ResponseEntity<String> icsResponse(Appointment appointment, String ics) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("agendamento-" + appointment.getId() + ".ics").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ics);
    }
}
