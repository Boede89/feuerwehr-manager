package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonCourseCompletionRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
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
    private final UserRepository userRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public int warnDays(long unitId) {
        return atemschutzSettingsService.warnDays(unitId);
    }

    @Transactional
    public CarrierListResult listCarrierOverviews(long unitId, boolean includeHealthDetails, String filter) {
        atemschutzSettingsService.ensureSettings(unitId);
        syncCarriersFromAgt(unitId);
        boolean testData = testModeService.isEnabled();
        List<AtemschutzCarrier> carriers = carrierRepository.findByUnitId(unitId, testData);
        if (carriers.isEmpty()) {
            CarrierListStats emptyStats = new CarrierListStats(0, 0, 0, 0);
            return new CarrierListResult(
                    List.of(),
                    emptyStats,
                    emptyStats,
                    atemschutzSettingsService.agtCourseName(unitId),
                    atemschutzSettingsService.isAgtCourseConfigured(unitId));
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
            CarrierTauglichkeitStatus tauglichkeit = computeTauglichkeit(summaries, carrier.getStatus());
            all.add(new CarrierOverview(
                    carrier,
                    summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG),
                    summaries,
                    tauglichkeit));
        }
        List<CarrierOverview> activeCarriers =
                all.stream().filter(row -> row.carrier().getStatus() == AtemschutzCarrierStatus.ACTIVE).toList();
        CarrierListStats stats = computeStats(activeCarriers);
        CarrierListStats statsAll = computeStats(all);
        List<CarrierOverview> filtered = applyFilter(all, filter);
        return new CarrierListResult(
                filtered,
                stats,
                statsAll,
                atemschutzSettingsService.agtCourseName(unitId),
                atemschutzSettingsService.isAgtCourseConfigured(unitId));
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
        Long courseId = atemschutzSettingsService.agtCourseId(unitId).orElse(null);
        if (courseId == null) {
            return;
        }
        List<Person> agtPersons =
                completionRepository.findPersonsWithCompletedCourseId(unitId, testData, courseId);
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
            long carrierId, AtemschutzFitnessType type, LocalDate validFrom, long createdByUserId) {
        AtemschutzCarrier carrier = requireCarrier(carrierId);
        if (validFrom == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Nachweis-Typ fehlt.");
        }
        LocalDate validUntil = computeValidUntil(type, validFrom, carrier.getPerson().getBirthdate());
        User createdBy = userRepository
                .findById(createdByUserId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
        record.setCarrier(carrier);
        record.setRecordType(type);
        record.setValidFrom(validFrom);
        record.setValidUntil(validUntil);
        record.setCreatedBy(createdBy);
        record.setTestData(testModeService.isEnabled());
        return fitnessRecordRepository.save(record);
    }

    public static LocalDate computeValidUntil(
            AtemschutzFitnessType type, LocalDate validFrom, LocalDate birthdate) {
        if (validFrom == null) {
            throw new IllegalArgumentException("Datum ist erforderlich.");
        }
        int years =
                switch (type) {
                    case STRECKEN, UEBUNG -> 1;
                    case G26_UNTERSUCHUNG -> {
                        if (birthdate == null) {
                            yield 1;
                        }
                        int age = Period.between(birthdate, validFrom).getYears();
                        yield age < 50 ? 3 : 1;
                    }
                };
        return validFrom.plusYears(years);
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
        String createdByDisplay = formatCreatedBy(record);
        if (!includeHealthDetails && record.getRecordType().healthData()) {
            return new FitnessRecordView(
                    record.getId(),
                    record.getRecordType(),
                    level,
                    record.getValidFrom(),
                    record.getValidUntil(),
                    null,
                    null,
                    createdByDisplay,
                    blankToNull(record.getSourceLabel()));
        }
        return new FitnessRecordView(
                record.getId(),
                record.getRecordType(),
                level,
                record.getValidFrom(),
                record.getValidUntil(),
                record.getPhysician(),
                record.getResultNotes(),
                createdByDisplay,
                blankToNull(record.getSourceLabel()));
    }

    private static String formatCreatedBy(AtemschutzFitnessRecord record) {
        if (record.getCreatedBy() == null) {
            return null;
        }
        String name = record.getCreatedBy().getDisplayName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return record.getCreatedBy().getUsername();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static boolean isOverallTauglich(
            Map<AtemschutzFitnessType, FitnessStatusView> summaries, AtemschutzCarrierStatus status) {
        return computeTauglichkeit(summaries, status) == CarrierTauglichkeitStatus.TAUGLICH;
    }

    public static CarrierTauglichkeitStatus computeTauglichkeit(
            Map<AtemschutzFitnessType, FitnessStatusView> summaries, AtemschutzCarrierStatus status) {
        if (status != AtemschutzCarrierStatus.ACTIVE) {
            return CarrierTauglichkeitStatus.NICHT_TAUGLICH;
        }
        if (isAllFitnessOk(summaries)) {
            return CarrierTauglichkeitStatus.TAUGLICH;
        }
        if (isUebungAbgelaufenOnly(summaries)) {
            return CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN;
        }
        return CarrierTauglichkeitStatus.NICHT_TAUGLICH;
    }

    private static boolean isAllFitnessOk(Map<AtemschutzFitnessType, FitnessStatusView> summaries) {
        for (AtemschutzFitnessType type : AtemschutzFitnessType.values()) {
            FitnessStatusView view = summaries.get(type);
            if (view == null || view.level() != AtemschutzFitnessLevel.OK) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUebungAbgelaufenOnly(Map<AtemschutzFitnessType, FitnessStatusView> summaries) {
        FitnessStatusView g26 = summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG);
        FitnessStatusView uebung = summaries.get(AtemschutzFitnessType.UEBUNG);
        FitnessStatusView strecke = summaries.get(AtemschutzFitnessType.STRECKEN);
        return g26 != null
                && g26.level() == AtemschutzFitnessLevel.OK
                && strecke != null
                && strecke.level() == AtemschutzFitnessLevel.OK
                && uebung != null
                && uebung.level() == AtemschutzFitnessLevel.OVERDUE;
    }

    private static List<CarrierOverview> applyFilter(List<CarrierOverview> carriers, String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return carriers;
        }
        if ("tauglich".equalsIgnoreCase(filter)) {
            return carriers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.TAUGLICH)
                    .toList();
        }
        if ("uebung_abgelaufen".equalsIgnoreCase(filter) || "uebungabgelaufen".equalsIgnoreCase(filter)) {
            return carriers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN)
                    .toList();
        }
        if ("nicht_tauglich".equalsIgnoreCase(filter) || "nichttauglich".equalsIgnoreCase(filter)) {
            return carriers.stream()
                    .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.NICHT_TAUGLICH)
                    .toList();
        }
        return carriers;
    }

    @Transactional(readOnly = true)
    public UebungPlanResult planUebung(
            long unitId,
            boolean includeHealthDetails,
            LocalDate uebungsDatum,
            Set<AtemschutzPlanStatus> statusFilter,
            int limit) {
        if (uebungsDatum == null) {
            throw new IllegalArgumentException("Bitte ein Übungsdatum angeben.");
        }
        Set<AtemschutzPlanStatus> effectiveFilter = statusFilter == null || statusFilter.isEmpty()
                ? EnumSet.copyOf(AtemschutzPlanStatus.DEFAULT_SELECTED.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()))
                : EnumSet.copyOf(statusFilter);

        CarrierListResult allCarriers = listCarrierOverviews(unitId, includeHealthDetails, "all");
        List<UebungPlanRow> matches = new ArrayList<>();
        for (CarrierOverview row : allCarriers.carriers()) {
            if (row.carrier().getStatus() != AtemschutzCarrierStatus.ACTIVE) {
                continue;
            }
            AtemschutzPlanStatus planStatus = computePlanStatus(row.summaries());
            if (!effectiveFilter.contains(planStatus)) {
                continue;
            }
            matches.add(new UebungPlanRow(row, planStatus));
        }

        matches.sort(Comparator.comparing(
                row -> row.overview().summaries().get(AtemschutzFitnessType.UEBUNG),
                Comparator.comparing(
                        view -> view != null && view.validUntil() != null ? view.validUntil() : LocalDate.MAX)));

        int effectiveLimit = limit > 0 ? limit : matches.size();
        List<UebungPlanRow> limited = matches.size() <= effectiveLimit ? matches : matches.subList(0, effectiveLimit);

        return new UebungPlanResult(uebungsDatum, limit, effectiveFilter, limited, matches.size());
    }

    public static AtemschutzPlanStatus computePlanStatus(Map<AtemschutzFitnessType, FitnessStatusView> summaries) {
        boolean streckeExpired = isPlanExpired(summaries.get(AtemschutzFitnessType.STRECKEN));
        boolean g26Expired = isPlanExpired(summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG));
        boolean uebungExpired = isPlanExpired(summaries.get(AtemschutzFitnessType.UEBUNG));
        boolean streckeWarn = isPlanWarn(summaries.get(AtemschutzFitnessType.STRECKEN));
        boolean g26Warn = isPlanWarn(summaries.get(AtemschutzFitnessType.G26_UNTERSUCHUNG));
        boolean uebungWarn = isPlanWarn(summaries.get(AtemschutzFitnessType.UEBUNG));

        if (streckeExpired || g26Expired || uebungExpired) {
            if (uebungExpired && !streckeExpired && !g26Expired) {
                return AtemschutzPlanStatus.UEBUNG_ABGELAUFEN;
            }
            return AtemschutzPlanStatus.ABGELAUFEN;
        }
        if (streckeWarn || g26Warn || uebungWarn) {
            return AtemschutzPlanStatus.WARNUNG;
        }
        return AtemschutzPlanStatus.TAUGLICH;
    }

    private static boolean isPlanExpired(FitnessStatusView view) {
        if (view == null) {
            return true;
        }
        return view.level() == AtemschutzFitnessLevel.OVERDUE || view.level() == AtemschutzFitnessLevel.MISSING;
    }

    private static boolean isPlanWarn(FitnessStatusView view) {
        return view != null && view.level() == AtemschutzFitnessLevel.WARN;
    }

    private static CarrierListStats computeStats(List<CarrierOverview> carriers) {
        int tauglich = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.TAUGLICH)
                .count();
        int uebungAbgelaufen = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.UEBUNG_ABGELAUFEN)
                .count();
        int nichtTauglich = (int) carriers.stream()
                .filter(row -> row.tauglichkeit() == CarrierTauglichkeitStatus.NICHT_TAUGLICH)
                .count();
        return new CarrierListStats(carriers.size(), tauglich, uebungAbgelaufen, nichtTauglich);
    }

    public record UebungPlanResult(
            LocalDate uebungsDatum,
            int requestedLimit,
            Set<AtemschutzPlanStatus> statusFilter,
            List<UebungPlanRow> carriers,
            int totalMatches) {}

    public record UebungPlanRow(CarrierOverview overview, AtemschutzPlanStatus planStatus) {}

    public record CarrierListResult(
            List<CarrierOverview> carriers,
            CarrierListStats stats,
            CarrierListStats statsAll,
            String agtCourseName,
            boolean agtCourseConfigured) {}

    public record CarrierListStats(int total, int tauglich, int uebungAbgelaufen, int nichtTauglich) {}

    public record CarrierOverview(
            AtemschutzCarrier carrier,
            FitnessStatusView g26,
            Map<AtemschutzFitnessType, FitnessStatusView> summaries,
            CarrierTauglichkeitStatus tauglichkeit) {}

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
            String resultNotes,
            String createdByDisplay,
            String sourceLabel) {}
}
