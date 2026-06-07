package br.com.clube_quinze.api.service.settings;

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

    // ── helpers ─────────────────────────────────────────────────────────────

    private void upsert(String key, String value) {
        AppSetting s = repository.findById(key).orElse(new AppSetting(key, value));
        s.setValue(value);
        repository.save(s);
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
