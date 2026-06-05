package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtemschutzService {

    private final UnitRepository unitRepository;
    private final PersonalService personalService;
    private final AtemschutzCarrierRepository carrierRepository;
    private final AtemschutzFitnessRecordRepository fitnessRecordRepository;
    private final TestModeService testModeService;
    private final GlobalSettingsService globalSettingsService;

    @Transactional(readOnly = true)
    public int warnDays() {
        return globalSettingsService.get().getQualificationWarnDays();
    }

    @Transactional(readOnly = true)
    public List<CarrierOverview> listCarrierOverviews(long unitId, boolean includeHealthDetails) {
        boolean testData = testModeService.isEnabled();
        List<AtemschutzCarrier> carriers = carrierRepository.findByUnitId(unitId, testData);
        if (carriers.isEmpty()) {
            return List.of();
        }
        List<Long> carrierIds = carriers.stream().map(AtemschutzCarrier::getId).toList();
        Map<Long, AtemschutzFitnessRecord> latestG26 = latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.G26_UNTERSUCHUNG, testData);
        int warnDays = warnDays();
        LocalDate today = LocalDate.now();
        List<CarrierOverview> result = new ArrayList<>();
        for (AtemschutzCarrier carrier : carriers) {
            AtemschutzFitnessRecord g26 = latestG26.get(carrier.getId());
            FitnessStatusView g26View = toFitnessView(g26, warnDays, today, includeHealthDetails);
            result.add(new CarrierOverview(carrier, g26View));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AtemschutzCarrier requireCarrier(long carrierId) {
        return carrierRepository
                .findByIdAndTestData(carrierId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Geräteträger nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public CarrierDetailView loadCarrierDetail(long carrierId, boolean includeHealthDetails) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        boolean testData = testModeService.isEnabled();
        List<AtemschutzFitnessRecord> records = fitnessRecordRepository.findByCarrierId(carrierId, testData);
        int warnDays = warnDays();
        LocalDate today = LocalDate.now();
        Map<AtemschutzFitnessType, FitnessStatusView> summaries = new EnumMap<>(AtemschutzFitnessType.class);
        for (AtemschutzFitnessType type : AtemschutzFitnessType.values()) {
            AtemschutzFitnessRecord latest = records.stream()
                    .filter(r -> r.getRecordType() == type)
                    .max(Comparator.comparing(AtemschutzFitnessRecord::getValidUntil)
                            .thenComparing(AtemschutzFitnessRecord::getId))
                    .orElse(null);
            summaries.put(type, toFitnessView(latest, warnDays, today, includeHealthDetails));
        }
        List<FitnessRecordView> recordViews = records.stream()
                .map(r -> toRecordView(r, includeHealthDetails))
                .toList();
        return new CarrierDetailView(carrier, summaries, recordViews);
    }

    @Transactional(readOnly = true)
    public List<Person> listAssignablePersons(long unitId) {
        boolean testData = testModeService.isEnabled();
        List<Long> assigned = carrierRepository.findPersonIdsByUnitId(unitId, testData);
        return personalService.listPersons(unitId).stream()
                .filter(p -> !assigned.contains(p.getId()))
                .toList();
    }

    @Transactional
    public AtemschutzCarrier registerCarrier(long unitId, long personId, AtemschutzCarrierStatus status, String notes) {
        Person person = personalService.requirePerson(personId);
        if (!person.getUnit().getId().equals(unitId)) {
            throw new IllegalArgumentException("Person gehört nicht zur Einheit.");
        }
        if (carrierRepository.existsByPersonId(personId)) {
            throw new IllegalArgumentException("Person ist bereits als Geräteträger erfasst.");
        }
        Unit unit = unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        AtemschutzCarrier carrier = new AtemschutzCarrier();
        carrier.setUnit(unit);
        carrier.setPerson(person);
        carrier.setStatus(status != null ? status : AtemschutzCarrierStatus.ACTIVE);
        carrier.setNotes(blankToNull(notes));
        carrier.setTestData(testModeService.isEnabled());
        carrier.setProductionSourceId(null);
        return carrierRepository.save(carrier);
    }

    @Transactional
    public AtemschutzCarrier updateCarrier(long carrierId, AtemschutzCarrierStatus status, String notes) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        if (status != null) {
            carrier.setStatus(status);
        }
        carrier.setNotes(blankToNull(notes));
        return carrierRepository.save(carrier);
    }

    @Transactional
    public void removeCarrier(long carrierId) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        carrierRepository.delete(carrier);
    }

    @Transactional
    public AtemschutzFitnessRecord addFitnessRecord(
            long carrierId,
            AtemschutzFitnessType type,
            LocalDate validFrom,
            LocalDate validUntil,
            String physician,
            String resultNotes) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        validateFitnessRecord(type, validUntil);
        AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
        record.setCarrier(carrier);
        record.setRecordType(type);
        record.setValidFrom(validFrom);
        record.setValidUntil(validUntil);
        record.setPhysician(blankToNull(physician));
        record.setResultNotes(blankToNull(resultNotes));
        record.setTestData(testModeService.isEnabled());
        return fitnessRecordRepository.save(record);
    }

    @Transactional
    public void deleteFitnessRecord(long recordId) {
        AtemschutzFitnessRecord record = fitnessRecordRepository
                .findByIdAndTestData(recordId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Nachweis nicht gefunden"));
        fitnessRecordRepository.delete(record);
    }

    public boolean canViewHealthData(AppUserDetails actor, Person person) {
        if (actor == null || person == null) {
            return false;
        }
        if (actor.getRole().isAdminLevel()) {
            return true;
        }
        if (person.getUser() != null && person.getUser().getId().equals(actor.getUserId())) {
            return true;
        }
        return false;
    }

    public static AtemschutzFitnessLevel computeLevel(LocalDate validUntil, int warnDays, LocalDate today) {
        if (validUntil == null) {
            return AtemschutzFitnessLevel.MISSING;
        }
        if (validUntil.isBefore(today)) {
            return AtemschutzFitnessLevel.OVERDUE;
        }
        if (!validUntil.isAfter(today.plusDays(warnDays))) {
            return AtemschutzFitnessLevel.WARN;
        }
        return AtemschutzFitnessLevel.OK;
    }

    private Map<Long, AtemschutzFitnessRecord> latestRecordsByCarrier(
            List<Long> carrierIds, AtemschutzFitnessType type, boolean testData) {
        if (carrierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AtemschutzFitnessRecord> result = new HashMap<>();
        for (AtemschutzFitnessRecord record : fitnessRecordRepository.findByCarrierIdsAndType(carrierIds, type, testData)) {
            result.putIfAbsent(record.getCarrier().getId(), record);
        }
        return result;
    }

    private FitnessStatusView toFitnessView(
            AtemschutzFitnessRecord record, int warnDays, LocalDate today, boolean includeHealthDetails) {
        if (record == null) {
            return new FitnessStatusView(AtemschutzFitnessLevel.MISSING, null, null, null, null);
        }
        AtemschutzFitnessLevel level = computeLevel(record.getValidUntil(), warnDays, today);
        if (!includeHealthDetails) {
            return new FitnessStatusView(level, record.getValidUntil(), null, null, null);
        }
        return new FitnessStatusView(
                level,
                record.getValidUntil(),
                record.getValidFrom(),
                record.getPhysician(),
                record.getResultNotes());
    }

    private FitnessRecordView toRecordView(AtemschutzFitnessRecord record, boolean includeHealthDetails) {
        int warnDays = warnDays();
        AtemschutzFitnessLevel level = computeLevel(record.getValidUntil(), warnDays, LocalDate.now());
        if (!includeHealthDetails && record.getRecordType().healthData()) {
            return new FitnessRecordView(
                    record.getId(),
                    record.getRecordType(),
                    level,
                    record.getValidFrom(),
                    record.getValidUntil(),
                    null,
                    null);
        }
        return new FitnessRecordView(
                record.getId(),
                record.getRecordType(),
                level,
                record.getValidFrom(),
                record.getValidUntil(),
                record.getPhysician(),
                record.getResultNotes());
    }

    private static void validateFitnessRecord(AtemschutzFitnessType type, LocalDate validUntil) {
        if (type == null) {
            throw new IllegalArgumentException("Nachweis-Typ fehlt.");
        }
        if (validUntil == null) {
            throw new IllegalArgumentException("Gültig bis ist erforderlich.");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CarrierOverview(AtemschutzCarrier carrier, FitnessStatusView g26) {}

    public record CarrierDetailView(
            AtemschutzCarrier carrier,
            Map<AtemschutzFitnessType, FitnessStatusView> summaries,
            List<FitnessRecordView> records) {}

    public record FitnessStatusView(
            AtemschutzFitnessLevel level,
            LocalDate validUntil,
            LocalDate validFrom,
            String physician,
            String resultNotes) {}

    public record FitnessRecordView(
            long id,
            AtemschutzFitnessType type,
            AtemschutzFitnessLevel level,
            LocalDate validFrom,
            LocalDate validUntil,
            String physician,
            String resultNotes) {}
}
