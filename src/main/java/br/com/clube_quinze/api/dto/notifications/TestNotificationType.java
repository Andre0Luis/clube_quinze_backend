package br.com.clube_quinze.api.dto.notifications;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Tipos de notificação disponíveis para o endpoint de teste.
 * Cada valor carrega o kind, título e data payload que espelha
 * exatamente o que o sistema envia em produção.
 *
 * <p>O {@code body} é calculado no momento do envio (não na inicialização do enum)
 * para garantir que o horário seja sempre atual e para evitar o problema de
 * inicialização de campos estáticos em enums Java (os constants são inicializados
 * antes dos campos static, o que causaria NPE se FMT fosse referenciado inline).
 */
public enum TestNotificationType {

    TEST       ("TEST",                   "🚀 Notificação de teste",     "em breve",        "test",       null,     null),
    REMINDER_24H("APPOINTMENT_REMINDER_PUSH", "📅 Lembrete de agendamento", "amanhã",          "reminder",   "-24h",   24 * 60),
    REMINDER_3H ("APPOINTMENT_REMINDER_PUSH", "📅 Lembrete de agendamento", "em 3 horas",      "reminder",   "-3h",    3 * 60),
    REMINDER_1H ("APPOINTMENT_REMINDER_PUSH", "⏰ Agendamento em breve",    "em 1 hora",       "reminder",   "-1h",    60),
    REMINDER_30MIN("APPOINTMENT_REMINDER_PUSH","⏰ Agendamento em breve",   "em 30 minutos",   "reminder",   "-30min", 30),
    RESCHEDULED ("APPOINTMENT_RESCHEDULED",   "🔄 Agendamento reagendado", "em breve",        "rescheduled",null,     48 * 60),
    CANCELLED   ("CANCELLED",                 "❌ Agendamento cancelado",  null,              "cancelled",  null,     null);

    // ── campos ────────────────────────────────────────────────────────────────

    public final String kind;
    public final String title;
    /** Rótulo do offset, ex: "amanhã", "em 1 hora". Null para tipos sem horário. */
    private final String offsetLabel;
    private final String dataKind;
    private final String dataOffset;
    /** Minutos a somar ao now() para compor o body. Null = sem horário no body. */
    private final Integer offsetMinutes;

    TestNotificationType(String kind, String title, String offsetLabel,
                         String dataKind, String dataOffset, Integer offsetMinutes) {
        this.kind          = kind;
        this.title         = title;
        this.offsetLabel   = offsetLabel;
        this.dataKind      = dataKind;
        this.dataOffset    = dataOffset;
        this.offsetMinutes = offsetMinutes;
    }

    // ── body dinâmico (calculado no envio, não na inicialização) ─────────────

    public String buildBody() {
        return switch (this) {
            case TEST       -> "Se você viu isso, a integração FCM está funcionando!";
            case CANCELLED  -> "Seu agendamento foi cancelado pelo clube.";
            default         -> {
                String date = offsetMinutes != null ? " — " + formattedNow(offsetMinutes) : "";
                yield switch (this) {
                    case RESCHEDULED -> "Seu agendamento foi remarcado para" + date;
                    default          -> "Você tem um agendamento " + offsetLabel + date;
                };
            }
        };
    }

    public Map<String, Object> buildData() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("kind", dataKind);
        m.put("appointmentId", "0");
        if (dataOffset    != null) m.put("offset",      dataOffset);
        if (offsetLabel   != null) m.put("offsetLabel", offsetLabel);
        return m;
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static String formattedNow(int minutes) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(minutes).format(fmt);
    }
}
