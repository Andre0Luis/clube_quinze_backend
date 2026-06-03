package br.com.clube_quinze.api.dto.notifications;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Tipos de notificação disponíveis para o endpoint de teste.
 * Cada valor carrega o kind, título, corpo e data payload que espelha
 * exatamente o que o sistema envia em produção.
 */
public enum TestNotificationType {

    /** Verifica apenas que o FCM está funcionando e o token ativo. */
    TEST(
            "TEST",
            "🚀 Notificação de teste",
            "Se você viu isso, a integração FCM está funcionando!",
            Map.of("kind", "test")
    ),

    /** Simula o lembrete de 24h antes do agendamento. */
    REMINDER_24H(
            "APPOINTMENT_REMINDER_PUSH",
            "📅 Lembrete de agendamento",
            "Você tem um agendamento amanhã — " + formattedNow(24 * 60),
            Map.of("kind", "reminder", "appointmentId", "0", "offset", "-24h", "offsetLabel", "amanhã")
    ),

    /** Simula o lembrete de 3h antes do agendamento. */
    REMINDER_3H(
            "APPOINTMENT_REMINDER_PUSH",
            "📅 Lembrete de agendamento",
            "Você tem um agendamento em 3 horas — " + formattedNow(3 * 60),
            Map.of("kind", "reminder", "appointmentId", "0", "offset", "-3h", "offsetLabel", "em 3 horas")
    ),

    /** Simula o lembrete de 1h antes do agendamento. */
    REMINDER_1H(
            "APPOINTMENT_REMINDER_PUSH",
            "⏰ Agendamento em breve",
            "Você tem um agendamento em 1 hora — " + formattedNow(60),
            Map.of("kind", "reminder", "appointmentId", "0", "offset", "-1h", "offsetLabel", "em 1 hora")
    ),

    /** Simula o lembrete de 30min antes do agendamento. */
    REMINDER_30MIN(
            "APPOINTMENT_REMINDER_PUSH",
            "⏰ Agendamento em breve",
            "Você tem um agendamento em 30 minutos — " + formattedNow(30),
            Map.of("kind", "reminder", "appointmentId", "0", "offset", "-30min", "offsetLabel", "em 30 minutos")
    ),

    /** Simula a notificação de reagendamento. */
    RESCHEDULED(
            "APPOINTMENT_RESCHEDULED",
            "🔄 Agendamento reagendado",
            "Seu agendamento foi remarcado para " + formattedNow(60 * 48),
            Map.of("kind", "rescheduled", "appointmentId", "0")
    );

    // ── campos ────────────────────────────────────────────────────────────────

    public final String kind;
    public final String title;
    public final String body;
    public final Map<String, Object> data;

    TestNotificationType(String kind, String title, String body, Map<String, Object> data) {
        this.kind  = kind;
        this.title = title;
        this.body  = body;
        this.data  = data;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private static String formattedNow(int offsetMinutes) {
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(offsetMinutes).format(FMT);
    }
}
