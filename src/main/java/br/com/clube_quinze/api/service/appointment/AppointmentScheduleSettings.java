package br.com.clube_quinze.api.service.appointment;

import java.time.Duration;
import java.time.LocalTime;

public final class AppointmentScheduleSettings {

    public static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(21, 0);
    public static final Duration SLOT_DURATION = Duration.ofMinutes(30);

    /** Granularidade da agenda: início e duração dos atendimentos são múltiplos de 15 minutos. */
    public static final int GRID_MINUTES = 15;
    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 480;
    /** Duração padrão sugerida quando o admin não informa (e a do cliente fora do plano Select). */
    public static final int DEFAULT_DURATION_MINUTES = 30;
    /** Duração padrão do plano Select. */
    public static final int SELECT_DURATION_MINUTES = 120;
    public static final LocalTime DEFAULT_RECOMMENDED_TIME = LocalTime.of(10, 0);
    public static final int DEFAULT_RECURRING_MONTHS = 3;
    public static final String DEFAULT_APPOINTMENT_TIME_PREFERENCE_KEY = "default_appointment_time";

    private AppointmentScheduleSettings() {
    }
}
