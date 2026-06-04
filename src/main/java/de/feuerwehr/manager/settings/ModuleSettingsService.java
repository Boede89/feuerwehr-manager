package de.feuerwehr.manager.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModuleSettingsService {

    private static final TypeReference<Map<String, Boolean>> MODULE_MAP_TYPE = new TypeReference<>() {};

    private final ApplicationSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    public Map<AppModule, Boolean> modulesEnabled() {
        Map<String, Boolean> raw = readRaw();
        Map<AppModule, Boolean> result = new EnumMap<>(AppModule.class);
        for (AppModule module : AppModule.values()) {
            result.put(module, raw.getOrDefault(module.key(), module == AppModule.PERSONAL));
        }
        return result;
    }

    public boolean isEnabled(AppModule module) {
        return modulesEnabled().getOrDefault(module, false);
    }

    @Transactional
    public void saveModules(Map<String, Boolean> updates) {
        Map<String, Boolean> raw = new LinkedHashMap<>(readRaw());
        for (AppModule module : AppModule.values()) {
            if (module.implemented() && updates.containsKey(module.key())) {
                raw.put(module.key(), Boolean.TRUE.equals(updates.get(module.key())));
            }
        }
        ApplicationSettings settings = settings();
        settings.setModulesJson(writeRaw(raw));
        settingsRepository.save(settings);
    }

    private Map<String, Boolean> readRaw() {
        ApplicationSettings settings = settings();
        String json = settings.getModulesJson();
        if (json == null || json.isBlank()) {
            return defaultRaw();
        }
        try {
            Map<String, Boolean> parsed = objectMapper.readValue(json, MODULE_MAP_TYPE);
            Map<String, Boolean> merged = defaultRaw();
            if (parsed != null) {
                merged.putAll(parsed);
            }
            return merged;
        } catch (Exception e) {
            return defaultRaw();
        }
    }

    private static Map<String, Boolean> defaultRaw() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (AppModule module : AppModule.values()) {
            defaults.put(module.key(), module == AppModule.PERSONAL);
        }
        return defaults;
    }

    private String writeRaw(Map<String, Boolean> raw) {
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Module konnten nicht gespeichert werden");
        }
    }

    private ApplicationSettings settings() {
        return settingsRepository
                .findById(ApplicationSettings.SINGLETON_ID)
                .orElseGet(this::createSettings);
    }

    private ApplicationSettings createSettings() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setId(ApplicationSettings.SINGLETON_ID);
        settings.setTestModeEnabled(false);
        settings.setModulesJson(writeRaw(defaultRaw()));
        return settingsRepository.save(settings);
    }
}
