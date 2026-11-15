package br.com.clube_quinze.api.service.google;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Implementação real do gateway: fala com o Google por HTTPS (OAuth 2.0 e Calendar API v3), sem SDK pesado. */
@Component
public class RestGoogleCalendarGateway implements GoogleCalendarGateway {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String API = "https://www.googleapis.com/calendar/v3";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GoogleProperties properties;
    private final RestClient http = RestClient.create();

    public RestGoogleCalendarGateway(GoogleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", GoogleProperties.SCOPES))
                // offline + consent: garante o refresh token mesmo se a conta já tinha autorizado antes.
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent select_account")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public GoogleTokens exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", "authorization_code");
        return tokens(postForm(form));
    }

    @Override
    public GoogleTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("grant_type", "refresh_token");
        return tokens(postForm(form));
    }

    @Override
    public void revoke(String token) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", token);
            http.post().uri(REVOKE_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            // best-effort: o token já pode ter sido revogado pelo próprio usuário
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<GoogleCalendarInfo> listCalendars(String accessToken) {
        Map<String, Object> body = call(() -> http.get()
                .uri(API + "/users/me/calendarList?minAccessRole=writer&maxResults=250")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve().body(Map.class));
        List<GoogleCalendarInfo> result = new ArrayList<>();
        Object items = body == null ? null : body.get("items");
        if (items instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> item = (Map<String, Object>) o;
                result.add(new GoogleCalendarInfo(
                        String.valueOf(item.get("id")),
                        String.valueOf(item.getOrDefault("summaryOverride", item.get("summary"))),
                        Boolean.TRUE.equals(item.get("primary")),
                        String.valueOf(item.get("accessRole"))));
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GoogleCalendarInfo createCalendar(String accessToken, String summary, String timeZone) {
        Map<String, Object> created = call(() -> http.post()
                .uri(API + "/calendars")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("summary", summary, "timeZone", timeZone))
                .retrieve().body(Map.class));
        return new GoogleCalendarInfo(String.valueOf(created.get("id")), String.valueOf(created.get("summary")), false, "owner");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String upsertEvent(String accessToken, String calendarId, String eventId, GoogleEvent event) {
        Map<String, Object> payload = eventPayload(event);
        if (eventId != null) {
            try {
                Map<String, Object> updated = http.put()
                        .uri(API + "/calendars/{cal}/events/{id}", calendarId, eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve().body(Map.class);
                return String.valueOf(updated.get("id"));
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() != 404 && ex.getStatusCode().value() != 410) {
                    throw translate(ex);
                }
                // o evento foi apagado no Google: cria de novo
            } catch (RestClientException ex) {
                throw new GoogleApiException("Falha ao atualizar o evento no Google", ex);
            }
        }
        Map<String, Object> created = call(() -> http.post()
                .uri(API + "/calendars/{cal}/events", calendarId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve().body(Map.class));
        return String.valueOf(created.get("id"));
    }

    @Override
    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        try {
            http.delete()
                    .uri(API + "/calendars/{cal}/events/{id}", calendarId, eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 410) {
                return; // já não existe
            }
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new GoogleApiException("Falha ao remover o evento no Google", ex);
        }
    }

    private Map<String, Object> eventPayload(GoogleEvent e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", e.summary());
        payload.put("description", e.description());
        if (e.location() != null && !e.location().isBlank()) {
            payload.put("location", e.location());
        }
        payload.put("start", Map.of("dateTime", LOCAL.format(e.start()), "timeZone", e.timeZone()));
        payload.put("end", Map.of("dateTime", LOCAL.format(e.end()), "timeZone", e.timeZone()));
        if (e.colorId() != null) {
            payload.put("colorId", e.colorId());
        }
        payload.put("reminders", Map.of("useDefault", true));
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(MultiValueMap<String, String> form) {
        return call(() -> http.post().uri(TOKEN_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(Map.class));
    }

    private GoogleTokens tokens(Map<String, Object> body) {
        Object expires = body.get("expires_in");
        return new GoogleTokens(
                (String) body.get("access_token"),
                (String) body.get("refresh_token"),
                expires instanceof Number n ? n.longValue() : 3600L,
                (String) body.get("scope"));
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new GoogleApiException("Falha ao falar com o Google", ex);
        }
    }

    /** 400 invalid_grant e 401 significam credencial inválida/revogada; o resto é falha passageira da API. */
    private RuntimeException translate(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString();
        if (status == 401 || (status == 400 && body.contains("invalid_grant"))) {
            return new GoogleAuthException("O Google recusou as credenciais (" + status + ")");
        }
        return new GoogleApiException("Erro da API do Google (" + status + ")", ex);
    }
}
