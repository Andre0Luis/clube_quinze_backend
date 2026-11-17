package br.com.clube_quinze.api.service.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuração do OAuth do Google (app.google.*). */
@Component
@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {

    /** Permissões pedidas: criar agenda própria, mexer em eventos e listar as agendas para o administrador escolher. */
    public static final String[] SCOPES = {
            "https://www.googleapis.com/auth/calendar.app.created",
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.calendarlist.readonly"
    };

    private String clientId = "";
    private String clientSecret = "";
    private String tokenEncryptionKey = "";
    private String redirectUri = "";
    private String appReturnUrl = "";
    private String newCalendarName = "Clube Quinze";

    /** A integração só existe quando o cliente OAuth e a chave de criptografia estão definidos. */
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !tokenEncryptionKey.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getTokenEncryptionKey() { return tokenEncryptionKey; }
    public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = tokenEncryptionKey; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getAppReturnUrl() { return appReturnUrl; }
    public void setAppReturnUrl(String appReturnUrl) { this.appReturnUrl = appReturnUrl; }
    public String getNewCalendarName() { return newCalendarName; }
    public void setNewCalendarName(String newCalendarName) { this.newCalendarName = newCalendarName; }
}
