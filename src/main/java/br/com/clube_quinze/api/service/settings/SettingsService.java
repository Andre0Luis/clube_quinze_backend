package br.com.clube_quinze.api.service.settings;

import br.com.clube_quinze.api.dto.settings.AppointmentConfirmationSettings;
import br.com.clube_quinze.api.exception.BusinessException;
import br.com.clube_quinze.api.model.settings.AppSetting;
import br.com.clube_quinze.api.repository.AppSettingRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso tipado às configurações da aplicação (tabela app_settings).
 */
@Service
public class SettingsService {

    public static final String KEY_ADMIN_REMINDER_ENABLED = "admin_reminder_enabled";
    public static final String KEY_ADMIN_REMINDER_OFFSETS = "admin_reminder_offsets";

    public static final String KEY_CONFIRMATION_ENABLED = "appointment_confirmation_enabled";
    public static final String KEY_CONFIRMATION_REQUEST_OFFSET = "appointment_confirmation_request_offset";
    public static final String KEY_CONFIRMATION_ADMIN_ALERT_OFFSET = "appointment_confirmation_admin_alert_offset";
    public static final String KEY_CLIENT_REMINDER_OFFSETS = "appointment_client_reminder_offsets";
    public static final String KEY_SKIP_REMINDERS_WHEN_CONFIRMED = "appointment_skip_reminders_when_confirmed";

    private static final boolean DEFAULT_CONFIRMATION_ENABLED = true;
    private static final int DEFAULT_CONFIRMATION_REQUEST_OFFSET = 90;
    private static final int DEFAULT_CONFIRMATION_ADMIN_ALERT_OFFSET = 60;
    private static final List<Integer> DEFAULT_CLIENT_REMINDER_OFFSETS = List.of(1440, 180, 60, 30);
    private static final List<Integer> DEFAULT_SKIP_REMINDERS_WHEN_CONFIRMED = List.of(60);

    private static final boolean DEFAULT_ADMIN_REMINDER_ENABLED = true;
    private static final List<Integer> DEFAULT_ADMIN_REMINDER_OFFSETS = List.of(60, 30);

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    // ── Admin reminders ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isAdminReminderEnabled() {
        return repository.findById(KEY_ADMIN_REMINDER_ENABLED)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(DEFAULT_ADMIN_REMINDER_ENABLED);
    }

    @Transactional(readOnly = true)
    public List<Integer> getAdminReminderOffsets() {
        return repository.findById(KEY_ADMIN_REMINDER_OFFSETS)
                .map(s -> parseOffsets(s.getValue()))
                .filter(list -> !list.isEmpty())
                .orElse(DEFAULT_ADMIN_REMINDER_OFFSETS);
    }

    @Transactional
    public void updateAdminReminderSettings(boolean enabled, List<Integer> offsets) {
        upsert(KEY_ADMIN_REMINDER_ENABLED, Boolean.toString(enabled));
        // normaliza: positivos, únicos, ordenados desc
        List<Integer> clean = new ArrayList<>(offsets.stream()
                .filter(o -> o != null && o > 0)
                .distinct()
                .sorted((a, b) -> b - a)
                .toList());
        upsert(KEY_ADMIN_REMINDER_OFFSETS, serializeOffsets(clean));
    }

    // ── Confirmação de agendamentos ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public AppointmentConfirmationSettings getAppointmentConfirmationSettings() {
        return new AppointmentConfirmationSettings(
                readBoolean(KEY_CONFIRMATION_ENABLED, DEFAULT_CONFIRMATION_ENABLED),
                readInt(KEY_CONFIRMATION_REQUEST_OFFSET, DEFAULT_CONFIRMATION_REQUEST_OFFSET),
                readInt(KEY_CONFIRMATION_ADMIN_ALERT_OFFSET, DEFAULT_CONFIRMATION_ADMIN_ALERT_OFFSET),
                readOffsets(KEY_CLIENT_REMINDER_OFFSETS, DEFAULT_CLIENT_REMINDER_OFFSETS, false),
                readOffsets(KEY_SKIP_REMINDERS_WHEN_CONFIRMED, DEFAULT_SKIP_REMINDERS_WHEN_CONFIRMED, true));
    }

    @Transactional
    public AppointmentConfirmationSettings updateAppointmentConfirmationSettings(AppointmentConfirmationSettings in) {
        if (in.adminAlertOffset() >= in.confirmationRequestOffset()) {
            throw new BusinessException(
                    "O aviso ao admin deve acontecer depois do pedido de confirmação ao cliente "
                            + "(prazo do aviso menor que o prazo do pedido)");
        }
        List<Integer> reminders = cleanOffsets(in.clientReminderOffsets());
        List<Integer> skip = cleanOffsets(in.skipRemindersWhenConfirmed());
        upsert(KEY_CONFIRMATION_ENABLED, Boolean.toString(in.enabled()));
        upsert(KEY_CONFIRMATION_REQUEST_OFFSET, String.valueOf(in.confirmationRequestOffset()));
        upsert(KEY_CONFIRMATION_ADMIN_ALERT_OFFSET, String.valueOf(in.adminAlertOffset()));
        upsert(KEY_CLIENT_REMINDER_OFFSETS, serializeOffsets(reminders));
        upsert(KEY_SKIP_REMINDERS_WHEN_CONFIRMED, serializeOffsets(skip));
        return getAppointmentConfirmationSettings();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void upsert(String key, String value) {
        AppSetting s = repository.findById(key).orElse(new AppSetting(key, value));
        s.setValue(value);
        repository.save(s);
    }

    private boolean readBoolean(String key, boolean fallback) {
        return repository.findById(key).map(s -> Boolean.parseBoolean(s.getValue())).orElse(fallback);
    }

    private int readInt(String key, int fallback) {
        return repository.findById(key).map(s -> {
            try {
                int v = Integer.parseInt(s.getValue().trim());
                return v > 0 ? v : fallback;
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }).orElse(fallback);
    }

    /** @param allowEmpty se verdadeiro, uma lista vazia gravada é respeitada (não cai no padrão). */
    private List<Integer> readOffsets(String key, List<Integer> fallback, boolean allowEmpty) {
        return repository.findById(key).map(s -> {
            List<Integer> parsed = parseOffsets(s.getValue());
            return parsed.isEmpty() && !allowEmpty ? fallback : parsed;
        }).orElse(fallback);
    }

    private List<Integer> cleanOffsets(List<Integer> offsets) {
        return new ArrayList<>(offsets.stream()
                .filter(o -> o != null && o > 0 && o <= 10080)
                .distinct()
                .sorted((a, b) -> b - a)
                .toList());
    }

    private List<Integer> parseOffsets(String csv) {
        List<Integer> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) return result;
        for (String part : csv.split(",")) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v > 0) result.add(v);
            } catch (NumberFormatException ignored) {
                // ignora valores inválidos
            }
        }
        return result;
    }

    private String serializeOffsets(List<Integer> offsets) {
        return offsets.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }
}
