package br.com.clube_quinze.api.controller;

import br.com.clube_quinze.api.dto.google.GoogleIntegrationStatus;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleApiException;
import br.com.clube_quinze.api.service.google.GoogleCalendarGateway.GoogleAuthException;
import br.com.clube_quinze.api.service.google.GoogleCalendarService;
import br.com.clube_quinze.api.service.google.GoogleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Endereço de retorno do login do Google (redirect URI). Abre no navegador do administrador: conclui a conexão e mostra
 * uma página com o botão para voltar ao aplicativo. Protegido pelo "state" assinado, não por JWT.
 */
@Controller
public class GoogleOAuthCallbackController {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthCallbackController.class);

    private final GoogleCalendarService service;
    private final GoogleProperties properties;

    public GoogleOAuthCallbackController(GoogleCalendarService service, GoogleProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/integrations/google/callback")
    public String callback(@RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error,
                           Model model) {
        String back = properties.getAppReturnUrl();
        if (error != null) {
            return result(model, false, "A conexão foi cancelada no Google. Nada foi alterado.", back + "?status=cancelled", null);
        }
        if (code == null || state == null) {
            return result(model, false, "Retorno inválido do Google. Tente conectar novamente pelo aplicativo.", back + "?status=error", null);
        }
        try {
            GoogleIntegrationStatus status = service.completeConnect(code, state);
            return result(model, true, "Conta do Google conectada. Volte ao aplicativo para escolher a agenda dos agendamentos.",
                    back + "?status=connected", status.googleEmail());
        } catch (BusinessException ex) {
            return result(model, false, ex.getMessage(), back + "?status=error", null);
        } catch (GoogleAuthException | GoogleApiException ex) {
            log.warn("Falha ao concluir a conexão com o Google: {}", ex.getMessage());
            return result(model, false, "Não foi possível concluir a conexão com o Google. Tente novamente em instantes.",
                    back + "?status=error", null);
        }
    }

    private String result(Model model, boolean success, String message, String returnUrl, String email) {
        model.addAttribute("success", success);
        model.addAttribute("message", message);
        model.addAttribute("returnUrl", returnUrl);
        model.addAttribute("googleEmail", email);
        return "google-callback";
    }
}
