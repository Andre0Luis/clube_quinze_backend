package br.com.clube_quinze.api.service.google;

import java.time.LocalDateTime;
import java.util.List;

/** Fronteira com as APIs do Google (OAuth e Calendar). Trocada por um falso nos testes. */
public interface GoogleCalendarGateway {

    record GoogleTokens(String accessToken, String refreshToken, long expiresInSeconds, String scope) {}

    record GoogleCalendarInfo(String id, String summary, boolean primary, String accessRole) {}

    /** Evento com horário de relógio (sem fuso) + o fuso em que ele deve ser lido. */
    record GoogleEvent(String summary, String description, String location,
                       LocalDateTime start, LocalDateTime end, String timeZone, String colorId) {}

    /** O Google recusou as credenciais (acesso revogado ou token inválido): a conta precisa ser reconectada. */
    class GoogleAuthException extends RuntimeException {
        public GoogleAuthException(String message) {
            super(message);
        }
    }

    /** Falha da API do Google que não é de autenticação (limite, indisponibilidade etc.): tenta de novo depois. */
    class GoogleApiException extends RuntimeException {
        public GoogleApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    String buildAuthorizationUrl(String state);

    GoogleTokens exchangeCode(String code);

    GoogleTokens refresh(String refreshToken);

    void revoke(String token);

    List<GoogleCalendarInfo> listCalendars(String accessToken);

    GoogleCalendarInfo createCalendar(String accessToken, String summary, String timeZone);

    /** Cria o evento (eventId nulo) ou atualiza; se o evento não existir mais, cria de novo. Devolve o id. */
    String upsertEvent(String accessToken, String calendarId, String eventId, GoogleEvent event);

    /** Remove o evento; se ele já não existir, não é erro. */
    void deleteEvent(String accessToken, String calendarId, String eventId);
}
