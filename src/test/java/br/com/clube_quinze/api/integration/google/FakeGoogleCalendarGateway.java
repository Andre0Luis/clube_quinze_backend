package br.com.clube_quinze.api.integration.google;

import br.com.clube_quinze.api.service.google.GoogleCalendarGateway;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Google falso para os testes: guarda os eventos em memória e registra as chamadas. */
public class FakeGoogleCalendarGateway implements GoogleCalendarGateway {

    public static final String REFRESH_TOKEN = "refresh-token-secreto-123";
    public static final String ACCOUNT_EMAIL = "quinze@gmail.com";

    public final Map<String, GoogleEvent> events = new LinkedHashMap<>();
    public final List<String> revoked = new ArrayList<>();
    public final List<String> createdCalendars = new ArrayList<>();
    public volatile boolean refreshFails;
    private final AtomicInteger counter = new AtomicInteger();

    public void reset() {
        events.clear();
        revoked.clear();
        createdCalendars.clear();
        refreshFails = false;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return "https://accounts.example/auth?state=" + state;
    }

    @Override
    public GoogleTokens exchangeCode(String code) {
        if ("sem-refresh".equals(code)) {
            return new GoogleTokens("access-1", null, 3600, "escopos");
        }
        return new GoogleTokens("access-1", REFRESH_TOKEN, 3600, "calendar.events calendar.app.created");
    }

    @Override
    public GoogleTokens refresh(String refreshToken) {
        if (refreshFails) {
            throw new GoogleAuthException("invalid_grant");
        }
        return new GoogleTokens("access-" + counter.incrementAndGet(), null, 3600, null);
    }

    @Override
    public void revoke(String token) {
        revoked.add(token);
    }

    @Override
    public List<GoogleCalendarInfo> listCalendars(String accessToken) {
        return List.of(
                new GoogleCalendarInfo(ACCOUNT_EMAIL, "Quinze", true, "owner"),
                new GoogleCalendarInfo("equipe@group.calendar.google.com", "Equipe", false, "writer"));
    }

    @Override
    public GoogleCalendarInfo createCalendar(String accessToken, String summary, String timeZone) {
        createdCalendars.add(summary + "|" + timeZone);
        return new GoogleCalendarInfo("cal-novo-" + createdCalendars.size() + "@group.calendar.google.com", summary, false, "owner");
    }

    @Override
    public String upsertEvent(String accessToken, String calendarId, String eventId, GoogleEvent event) {
        String id = eventId != null && events.containsKey(eventId) ? eventId : "evt-" + counter.incrementAndGet();
        events.put(id, event);
        return id;
    }

    @Override
    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        events.remove(eventId);
    }
}
