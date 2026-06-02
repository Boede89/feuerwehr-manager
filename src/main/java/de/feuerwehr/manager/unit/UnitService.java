package de.feuerwehr.manager.unit;

import de.feuerwehr.manager.settings.TestModeService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public List<Unit> findAllOrdered() {
        return unitRepository.findAllVisible(testModeService.isEnabled());
    }

    @Transactional(readOnly = true)
    public List<Unit> findActiveOrdered() {
        return unitRepository.findActiveVisible(testModeService.isEnabled());
    }

    @Transactional(readOnly = true)
    public Optional<Unit> findById(long id) {
        return unitRepository.findVisibleById(id, testModeService.isEnabled());
    }

    @Transactional(readOnly = true)
    public Optional<Unit> resolveActiveUnit(Long requestedId) {
        List<Unit> active = findActiveOrdered();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        if (requestedId != null) {
            for (Unit u : active) {
                if (u.getId().equals(requestedId)) {
                    return Optional.of(u);
                }
            }
        }
        return Optional.of(active.get(0));
    }

    @Transactional
    public Unit create(String name) {
        String trimmed = normalizeName(name);
        boolean testData = testModeService.testDataScope();
        if (unitRepository.existsByNameIgnoreCaseAndTestData(trimmed, testData)) {
            throw new IllegalArgumentException("Eine Einheit mit diesem Namen existiert bereits.");
        }
        Unit unit = new Unit();
        unit.setName(trimmed);
        unit.setActive(true);
        unit.setTestData(testData);
        unit = unitRepository.save(unit);
        ensureDiveraSettings(unit);
        return unit;
    }

    @Transactional
    public Unit update(long id, String name, boolean active) {
        Unit unit = unitRepository
                .findVisibleById(id, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        String trimmed = normalizeName(name);
        if (unitRepository.existsByNameIgnoreCaseAndTestDataAndIdNot(trimmed, unit.isTestData(), id)) {
            throw new IllegalArgumentException("Eine Einheit mit diesem Namen existiert bereits.");
        }
        unit.setName(trimmed);
        unit.setActive(active);
        ensureDiveraSettings(unit);
        return unitRepository.save(unit);
    }

    private void ensureDiveraSettings(Unit unit) {
        if (diveraSettingsRepository.findByUnitId(unit.getId()).isPresent()) {
            return;
        }
        UnitDiveraSettings settings = new UnitDiveraSettings();
        settings.setUnit(unit);
        settings.setApiBaseUrl("https://app.divera247.com");
        settings.setAccessKey("");
        diveraSettingsRepository.save(settings);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen für die Einheit eingeben.");
        }
        return name.trim();
    }
}
