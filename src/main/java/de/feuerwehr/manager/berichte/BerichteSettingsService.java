package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.divera.UnitDiveraStatusId;
import de.feuerwehr.manager.divera.DiveraMappingService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BerichteSettingsService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final UnitBerichteSettingsRepository settingsRepository;
    private final UnitRepository unitRepository;
    private final DiveraMappingService diveraMappingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UnitBerichteSettings ensureSettings(long unitId) {
        return settingsRepository
                .findByUnitId(unitId)
                .orElseGet(() -> createDefaults(unitId));
    }

    @Transactional
    public UnitBerichteSettings saveEinsatzSettings(
            long unitId,
            boolean importIncidentDataFromDivera,
            boolean importPersonnelFromDivera,
            List<String> personnelStatusIds) {
        UnitBerichteSettings settings = settingsRepository
                .findByUnitId(unitId)
                .orElseGet(() -> createDefaults(unitId));
        settings.setImportIncidentDataFromDivera(importIncidentDataFromDivera);
        settings.setImportPersonnelFromDivera(importPersonnelFromDivera);
        settings.setEinsatzPersonnelStatusIds(writeStatusIds(personnelStatusIds, unitId));
        return settingsRepository.save(settings);
    }

    public List<String> parsePersonnelStatusIds(UnitBerichteSettings settings) {
        if (settings == null
                || settings.getEinsatzPersonnelStatusIds() == null
                || settings.getEinsatzPersonnelStatusIds().isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(settings.getEinsatzPersonnelStatusIds(), STRING_LIST);
            return ids != null ? ids : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<UnitDiveraStatusId> listSelectableStatusIds(long unitId) {
        return diveraMappingService.listStatusIds(unitId);
    }

    private String writeStatusIds(List<String> statusIds, long unitId) {
        Set<String> allowed = new LinkedHashSet<>();
        for (UnitDiveraStatusId row : diveraMappingService.listStatusIds(unitId)) {
            allowed.add(row.getStatusId());
        }
        List<String> normalized = new ArrayList<>();
        if (statusIds != null) {
            for (String id : statusIds) {
                if (id != null && !id.isBlank() && allowed.contains(id.trim())) {
                    normalized.add(id.trim());
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            return "[]";
        }
    }

    private UnitBerichteSettings createDefaults(long unitId) {
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        UnitBerichteSettings settings = new UnitBerichteSettings();
        settings.setUnit(unit);
        settings.setImportIncidentDataFromDivera(false);
        settings.setImportPersonnelFromDivera(false);
        settings.setEinsatzPersonnelStatusIds("[]");
        return settingsRepository.save(settings);
    }
}
