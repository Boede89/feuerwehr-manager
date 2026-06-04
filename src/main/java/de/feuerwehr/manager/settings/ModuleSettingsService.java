package de.feuerwehr.manager.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
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

    private final UnitRepository unitRepository;
    private final ObjectMapper objectMapper;

    public Map<AppModule, Boolean> modulesEnabled(long unitId) {
        Map<String, Boolean> raw = readRaw(unitId);
        Map<AppModule, Boolean> result = new EnumMap<>(AppModule.class);
        for (AppModule module : AppModule.values()) {
            result.put(module, raw.getOrDefault(module.key(), module == AppModule.PERSONAL));
        }
        return result;
    }

    public boolean isEnabled(AppModule module, long unitId) {
        return modulesEnabled(unitId).getOrDefault(module, false);
    }

    @Transactional
    public void saveModules(long unitId, Map<String, Boolean> updates) {
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        Map<String, Boolean> raw = new LinkedHashMap<>(readRaw(unitId));
        for (AppModule module : AppModule.values()) {
            if (module.implemented() && updates.containsKey(module.key())) {
                raw.put(module.key(), Boolean.TRUE.equals(updates.get(module.key())));
            }
        }
        unit.setModulesJson(writeRaw(raw));
        unitRepository.save(unit);
    }

    @Transactional
    public void ensureDefaultModules(Unit unit) {
        if (unit.getModulesJson() == null || unit.getModulesJson().isBlank()) {
            unit.setModulesJson(writeRaw(defaultRaw()));
            unitRepository.save(unit);
        }
    }

    private Map<String, Boolean> readRaw(long unitId) {
        return unitRepository
                .findById(unitId)
                .map(unit -> parseJson(unit.getModulesJson()))
                .orElse(defaultRaw());
    }

    private Map<String, Boolean> parseJson(String json) {
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
}
