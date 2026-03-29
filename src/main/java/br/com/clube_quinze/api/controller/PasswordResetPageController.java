package br.com.clube_quinze.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the password-reset HTML page so that it works both
 * behind the Nginx reverse-proxy AND when accessed directly
 * through the Spring Boot application.
 */
@Controller
public class PasswordResetPageController {

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "reset-password-page";
    }
}
