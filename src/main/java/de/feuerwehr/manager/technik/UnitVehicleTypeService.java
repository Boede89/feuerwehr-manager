package de.feuerwehr.manager.technik;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitVehicleTypeService {

    private final UnitVehicleTypeRepository vehicleTypeRepository;
    private final UnitRepository unitRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional
    public void ensureDefaults(long unitId) {
        if (!vehicleTypeRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId).isEmpty()) {
            return;
        }
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        int order = 0;
        for (Map.Entry<String, String> entry : VehicleTypes.labels().entrySet()) {
            UnitVehicleType type = new UnitVehicleType();
            type.setUnit(unit);
            type.setTypeKey(entry.getKey());
            type.setLabel(entry.getValue());
            type.setSortOrder(++order);
            vehicleTypeRepository.save(type);
        }
    }

    @Transactional(readOnly = true)
    public List<UnitVehicleType> list(long unitId) {
        return vehicleTypeRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
    }

    @Transactional(readOnly = true)
    public Map<String, String> labelsMap(long unitId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (UnitVehicleType type : list(unitId)) {
            map.put(type.getTypeKey(), type.getLabel());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public String labelFor(long unitId, String key) {
        if (key == null || key.isBlank()) {
            return "—";
        }
        return labelsMap(unitId).getOrDefault(key, key);
    }

    @Transactional(readOnly = true)
    public String normalizeKey(long unitId, String key) {
        Map<String, String> labels = labelsMap(unitId);
        if (labels.isEmpty()) {
            return VehicleTypes.normalizeKey(key);
        }
        if (key == null || key.isBlank()) {
            return firstKey(labels);
        }
        String k = normalizeKeyInput(key);
        if (labels.containsKey(k)) {
            return k;
        }
        return labels.containsKey("sonstiges") ? "sonstiges" : firstKey(labels);
    }

    @Transactional
    public UnitVehicleType create(long unitId, String key, String label) {
        ensureDefaults(unitId);
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        String typeKey = normalizeKeyInput(key);
        if (typeKey.isEmpty()) {
            throw new IllegalArgumentException("Bitte einen Schlüssel für den Typ eingeben.");
        }
        if (!typeKey.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Schlüssel darf nur Kleinbuchstaben, Ziffern und Unterstriche enthalten.");
        }
        String typeLabel = requireLabel(label);
        if (vehicleTypeRepository.existsByUnitIdAndTypeKeyIgnoreCase(unitId, typeKey)) {
            throw new IllegalArgumentException("Ein Fahrzeugtyp mit diesem Schlüssel existiert bereits.");
        }
        UnitVehicleType type = new UnitVehicleType();
        type.setUnit(unit);
        type.setTypeKey(typeKey);
        type.setLabel(typeLabel);
        type.setSortOrder(vehicleTypeRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId).size() + 1);
        return vehicleTypeRepository.save(type);
    }

    @Transactional
    public void delete(long unitId, long typeId) {
        UnitVehicleType type = vehicleTypeRepository
                .findByIdAndUnitId(typeId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeugtyp nicht gefunden."));
        long inUse = vehicleRepository.countByUnitIdAndVehicleType(unitId, type.getTypeKey());
        if (inUse > 0) {
            throw new IllegalArgumentException(
                    "Fahrzeugtyp wird noch von " + inUse + " Fahrzeug(en) verwendet und kann nicht gelöscht werden.");
        }
        vehicleTypeRepository.delete(type);
    }

    private static String firstKey(Map<String, String> labels) {
        if (labels.containsKey("lkw")) {
            return "lkw";
        }
        return labels.keySet().iterator().next();
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Anzeigenamen eingeben.");
        }
        return label.trim();
    }

    static String normalizeKeyInput(String key) {
        return key.trim().toLowerCase().replace(' ', '_');
    }
}
