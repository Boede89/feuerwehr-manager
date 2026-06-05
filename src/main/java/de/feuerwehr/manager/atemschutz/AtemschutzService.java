package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonCourseCompletionRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final PersonCourseCompletionRepository completionRepository;
    private final AtemschutzSettingsService atemschutzSettingsService;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public int warnDays(long unitId) {
        return atemschutzSettingsService.warnDays(unitId);
    }

    @Transactional
    public CarrierListResult listCarrierOverviews(long unitId, boolean includeHealthDetails, String filter) {
        syncCarriersFromAgt(unitId);
        boolean testData = testModeService.isEnabled();
        List<AtemschutzCarrier> carriers = carrierRepository.findByUnitId(unitId, testData);
        if (carriers.isEmpty()) {
            return new CarrierListResult(List.of(), new CarrierListStats(0, 0, 0), atemschutzSettingsService.agtCourseName(unitId));
        }
        List<Long> carrierIds = carriers.stream().map(AtemschutzCarrier::getId).toList();
        int warnDays = warnDays(unitId);
        LocalDate today = LocalDate.now();
        Map<Long, AtemschutzFitnessRecord> latestG26 =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.G26_UNTERSUCHUNG, testData);
        Map<Long, AtemschutzFitnessRecord> latestUebung =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.UEBUNG, testData);
        Map<Long, AtemschutzFitnessRecord> latestStrecke =
                latestRecordsByCarrier(carrierIds, AtemschutzFitnessType.STRECKEN, testData);
        List<CarrierOverview> all = new ArrayList<>();
        for (AtemschutzCarrier carrier : carriers) {
            Map<AtemschutzFitnessType, FitnessStatusView> summaries = new EnumMap<>(AtemschutzFitnessType.class);
            summaries.put(
                    AtemschutzFitnessType.G26_UNTERSUCHUNG,
                    toFitnessView(latestG26.get(carrier.getId()), warnDays, today, includeHealthDetails));
            summaries.put(
                    AtemschutzFitnessType.UEBUNG,
                    toFitnessView(latestUebung.get(carrier.getId()), warnDays, today, includeHealthDetails));
            summaries.put(
                    AtemschutzFitnessType.STRECKEN,
                    toFitnessView(latestStrecke.get(carrier.getId()), warnDays, today, includeHealthDetails));
            boolean overallTauglich = isOverallTauglich(summaries, carrier.getStatus());
            all.add(new CarrierOverview(
                    carrier,
                    summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG),
                    summaries,
                    overallTauglich));
        }
        int tauglich = (int) all.stream().filter(CarrierOverview::overallTauglich).count();
        CarrierListStats stats = new CarrierListStats(all.size(), tauglich, all.size() - tauglich);
        List<CarrierOverview> filtered = applyFilter(all, filter);
        return new CarrierListResult(filtered, stats, atemschutzSettingsService.agtCourseName(unitId));
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
        int warnDays = warnDays(carrier.getUnit().getId());
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

    @Transactional
    public void syncCarriersFromAgt(long unitId) {
        boolean testData = testModeService.isEnabled();
        String courseName = atemschutzSettingsService.agtCourseName(unitId);
        List<Person> agtPersons =
                completionRepository.findPersonsWithCompletedCourse(unitId, testData, courseName);
        Set<Long> agtPersonIds = agtPersons.stream().map(Person::getId).collect(Collectors.toCollection(HashSet::new));
        Unit unit = unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
        for (Person person : agtPersons) {
            ensureCarrierForPerson(unit, person);
        }
        for (AtemschutzCarrier carrier : carrierRepository.findByUnitId(unitId, testData)) {
            if (!agtPersonIds.contains(carrier.getPerson().getId())) {
                carrierRepository.delete(carrier);
            }
        }
    }

    private void ensureCarrierForPerson(Unit unit, Person person) {
        if (carrierRepository.existsByPersonId(person.getId())) {
            return;
        }
        AtemschutzCarrier carrier = new AtemschutzCarrier();
        carrier.setUnit(unit);
        carrier.setPerson(person);
        carrier.setStatus(AtemschutzCarrierStatus.ACTIVE);
        carrier.setTestData(testModeService.isEnabled());
        carrierRepository.save(carrier);
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
        int warnDays = warnDays(record.getCarrier().getUnit().getId());
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

    public static boolean isOverallTauglich(
            Map<AtemschutzFitnessType, FitnessStatusView> summaries, AtemschutzCarrierStatus status) {
        if (status != AtemschutzCarrierStatus.ACTIVE) {
            return false;
        }
        for (AtemschutzFitnessType type : AtemschutzFitnessType.values()) {
            FitnessStatusView view = summaries.get(type);
            if (view == null || view.level() != AtemschutzFitnessLevel.OK) {
                return false;
            }
        }
        return true;
    }

    private static List<CarrierOverview> applyFilter(List<CarrierOverview> carriers, String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return carriers;
        }
        if ("tauglich".equalsIgnoreCase(filter)) {
            return carriers.stream().filter(CarrierOverview::overallTauglich).toList();
        }
        if ("nicht_tauglich".equalsIgnoreCase(filter) || "nichttauglich".equalsIgnoreCase(filter)) {
            return carriers.stream().filter(row -> !row.overallTauglich()).toList();
        }
        return carriers;
    }

    public record CarrierListResult(
            List<CarrierOverview> carriers, CarrierListStats stats, String agtCourseName) {}

    public record CarrierListStats(int total, int tauglich, int nichtTauglich) {}

    public record CarrierOverview(
            AtemschutzCarrier carrier,
            FitnessStatusView g26,
            Map<AtemschutzFitnessType, FitnessStatusView> summaries,
            boolean overallTauglich) {}

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
