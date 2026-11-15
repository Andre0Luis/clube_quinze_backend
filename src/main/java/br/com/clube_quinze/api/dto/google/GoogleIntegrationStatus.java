package br.com.clube_quinze.api.dto.google;

import java.io.Serializable;

/**
 * Estado da integração com o Google Agenda.
 * status: NOT_CONFIGURED (servidor sem credenciais), DISCONNECTED, NEEDS_CALENDAR (conta conectada, falta escolher a
 * agenda), READY (sincronizando) ou REVOKED (o Google recusou o acesso: reconectar).
 */
public record GoogleIntegrationStatus(boolean configured, String status, String googleEmail,
                                      String calendarId, String calendarSummary) implements Serializable {
}
