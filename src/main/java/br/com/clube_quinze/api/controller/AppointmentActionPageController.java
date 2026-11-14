package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import br.com.clube_quinze.api.repository.AppointmentRepository;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.Action;
import br.com.clube_quinze.api.service.appointment.AppointmentActionTokenService.ParsedToken;
import br.com.clube_quinze.api.service.appointment.AppointmentService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Página aberta pelos botões do e-mail de confirmação (Confirmar / Cancelar / Remarcar).
 * O GET apenas MOSTRA a ação e pede um clique; quem executa é o POST. Isso evita que scanners de e-mail
 * ("link prefetch") confirmem ou cancelem sozinhos só por abrirem o link.
 */
@Controller
public class AppointmentActionPageController {

    private static final DateTimeFormatter PT_BR_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private final AppointmentActionTokenService tokenService;
    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final String deepLink;

    public AppointmentActionPageController(AppointmentActionTokenService tokenService,
                                           AppointmentService appointmentService,
                                           AppointmentRepository appointmentRepository,
                                           @Value("${app.public.app-deep-link}") String deepLink) {
        this.tokenService = tokenService;
        this.appointmentService = appointmentService;
        this.appointmentRepository = appointmentRepository;
        this.deepLink = deepLink;
    }

    @GetMapping("/appointment-action")
    public String show(@RequestParam("token") String token, Model model) {
        Optional<ParsedToken> parsed = tokenService.parse(token);
        if (parsed.isEmpty()) {
            return error(model, "Link inválido ou expirado. Abra o aplicativo para gerenciar seu agendamento.");
        }
        Optional<Appointment> appointment = appointmentRepository.findById(parsed.get().appointmentId());
        if (appointment.isEmpty()) {
            return error(model, "Agendamento não encontrado.");
        }
        Appointment a = appointment.get();
        model.addAttribute("scheduledAt", a.getScheduledAt().format(PT_BR_FORMATTER));
        model.addAttribute("token", token);
        model.addAttribute("deepLink", deepLink + "/" + a.getId());

        Action action = parsed.get().action();
        if (action == Action.RESCHEDULE) {
            model.addAttribute("state", "RESCHEDULE");
            return "appointment-action";
        }
        if (a.getStatus() == AppointmentStatus.CANCELED) {
            return done(model, "Este agendamento já está cancelado.");
        }
        if (action == Action.CONFIRM && a.getStatus() == AppointmentStatus.CONFIRMED) {
            return done(model, "Seu agendamento já está confirmado. Até lá!");
        }
        model.addAttribute("state", action.name());
        return "appointment-action";
    }

    @PostMapping("/appointment-action")
    public String perform(@RequestParam("token") String token, Model model) {
        Optional<ParsedToken> parsed = tokenService.parse(token);
        if (parsed.isEmpty()) {
            return error(model, "Link inválido ou expirado. Abra o aplicativo para gerenciar seu agendamento.");
        }
        Long id = parsed.get().appointmentId();
        try {
            switch (parsed.get().action()) {
                case CONFIRM -> {
                    appointmentService.confirmViaLink(id);
                    return done(model, "Presença confirmada! Esperamos você no horário combinado.");
                }
                case CANCEL -> {
                    appointmentService.cancelViaLink(id);
                    return done(model, "Agendamento cancelado. Quando quiser, é só marcar um novo horário.");
                }
                default -> {
                    return error(model, "Para remarcar, abra o aplicativo e escolha o novo horário.");
                }
            }
        } catch (BusinessException ex) {
            return error(model, ex.getMessage());
        }
    }

    private String done(Model model, String message) {
        model.addAttribute("state", "DONE");
        model.addAttribute("message", message);
        return "appointment-action";
    }

    private String error(Model model, String message) {
        model.addAttribute("state", "ERROR");
        model.addAttribute("message", message);
        return "appointment-action";
    }
}
