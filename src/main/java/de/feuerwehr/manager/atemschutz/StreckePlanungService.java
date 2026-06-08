package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.atemschutz.AtemschutzService.CarrierOverview;
import de.feuerwehr.manager.atemschutz.AtemschutzService.FitnessStatusView;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StreckePlanungService {

    private final UnitRepository unitRepository;
    private final AtemschutzService atemschutzService;
    private final StreckeTerminRepository terminRepository;
    private final StreckeZuordnungRepository zuordnungRepository;
    private final AtemschutzCarrierRepository carrierRepository;
    private final UserRepository userRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public StreckePlanungView loadView(long unitId, boolean includeHealthDetails) {
        boolean testData = testModeService.isEnabled();
        int warnDays = atemschutzService.warnDays(unitId);
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(30);

        List<StreckeTermin> termine = terminRepository.findRecentByUnit(unitId, since, testData);
        List<Long> terminIds = termine.stream().map(StreckeTermin::getId).toList();
        Map<Long, List<StreckeZuordnung>> zuordnungenByTermin = zuordnungRepository.findByTerminIds(terminIds).stream()
                .collect(Collectors.groupingBy(z -> z.getTermin().getId()));

        Map<Long, CarrierOverview> carrierById = atemschutzService
                .listCarrierOverviews(unitId, includeHealthDetails, "all")
                .carriers()
                .stream()
                .filter(row -> row.carrier().getStatus() == AtemschutzCarrierStatus.ACTIVE)
                .collect(Collectors.toMap(row -> row.carrier().getId(), row -> row, (a, b) -> a));

        List<TerminView> terminViews = new ArrayList<>();
        for (StreckeTermin termin : termine) {
            List<StreckeZuordnung> zuordnungen =
                    zuordnungenByTermin.getOrDefault(termin.getId(), List.of());
            List<CarrierAssignmentView> teilnehmer = zuordnungen.stream()
                    .map(z -> toAssignmentView(z, carrierById.get(z.getCarrier().getId()), termin, warnDays, today))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(CarrierAssignmentView::name))
                    .toList();
            terminViews.add(toTerminView(termin, teilnehmer, today));
        }

        Map<Long, Long> carrierTerminMap = new HashMap<>();
        zuordnungenByTermin.forEach((terminId, list) -> list.forEach(z -> carrierTerminMap.put(
                z.getCarrier().getId(), terminId)));

        List<CarrierPoolView> pool = carrierById.values().stream()
                .filter(row -> !carrierTerminMap.containsKey(row.carrier().getId()))
                .map(row -> toPoolView(row, warnDays, today))
                .sorted(Comparator.comparing(
                        CarrierPoolView::daysUntilExpiry,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new StreckePlanungView(terminViews, pool, warnDays);
    }

    @Transactional
    public int createTermine(long unitId, Long userId, List<CreateTerminRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Termin angeben.");
        }
        Unit unit = requireUnit(unitId);
        User creator = userId == null ? null : userRepository.findById(userId).orElse(null);
        boolean testData = testModeService.isEnabled();
        int created = 0;
        for (CreateTerminRequest request : requests) {
            validateTerminRequest(request);
            StreckeTermin termin = new StreckeTermin();
            termin.setUnit(unit);
            termin.setTerminDatum(request.terminDatum());
            termin.setTerminZeit(request.terminZeit() != null ? request.terminZeit() : LocalTime.of(9, 0));
            termin.setOrt(blankToEmpty(request.ort()));
            termin.setMaxTeilnehmer(request.maxTeilnehmer() > 0 ? request.maxTeilnehmer() : 10);
            termin.setBemerkung(blankToNull(request.bemerkung()));
            termin.setTestData(testData);
            termin.setCreatedBy(creator);
            terminRepository.save(termin);
            created++;
        }
        return created;
    }

    @Transactional
    public void updateTermin(long unitId, long terminId, UpdateTerminRequest request) {
        validateTerminRequest(request);
        StreckeTermin termin = requireTermin(unitId, terminId);
        termin.setTerminDatum(request.terminDatum());
        termin.setTerminZeit(request.terminZeit() != null ? request.terminZeit() : LocalTime.of(9, 0));
        termin.setOrt(blankToEmpty(request.ort()));
        termin.setMaxTeilnehmer(request.maxTeilnehmer() > 0 ? request.maxTeilnehmer() : 10);
        termin.setBemerkung(blankToNull(request.bemerkung()));
    }

    @Transactional
    public void deleteTermin(long unitId, long terminId) {
        StreckeTermin termin = requireTermin(unitId, terminId);
        terminRepository.delete(termin);
    }

    @Transactional
    public void assignCarrier(long unitId, long terminId, long carrierId) {
        StreckeTermin termin = requireTermin(unitId, terminId);
        AtemschutzCarrier carrier = requireActiveCarrier(unitId, carrierId);
        if (zuordnungRepository.countByTerminId(terminId) >= termin.getMaxTeilnehmer()) {
            throw new IllegalArgumentException("Termin ist bereits voll.");
        }
        zuordnungRepository.deleteByCarrierId(carrierId);
        StreckeZuordnung zuordnung = new StreckeZuordnung();
        zuordnung.setTermin(termin);
        zuordnung.setCarrier(carrier);
        zuordnungRepository.save(zuordnung);
    }

    @Transactional
    public void removeAssignment(long unitId, long terminId, long carrierId) {
        requireTermin(unitId, terminId);
        requireActiveCarrier(unitId, carrierId);
        zuordnungRepository.deleteByTerminIdAndCarrierId(terminId, carrierId);
    }

    @Transactional
    public void removeCarrierFromAnyAssignment(long unitId, long carrierId) {
        requireActiveCarrier(unitId, carrierId);
        zuordnungRepository.deleteByCarrierId(carrierId);
    }

    @Transactional
    public AutoAssignResult autoAssign(long unitId) {
        boolean testData = testModeService.isEnabled();
        LocalDate today = LocalDate.now();

        List<CarrierOverview> unassigned = loadUnassignedCarriers(unitId);
        List<StreckeTermin> futureTermine = terminRepository.findRecentByUnit(unitId, today, testData).stream()
                .filter(t -> !t.getTerminDatum().isBefore(today))
                .toList();
        if (futureTermine.isEmpty()) {
            throw new IllegalArgumentException("Keine Termine mit freien Plätzen verfügbar.");
        }
        if (unassigned.isEmpty()) {
            return new AutoAssignResult(0, 0, "Alle Geräteträger sind bereits zugeordnet.");
        }

        Map<Long, Integer> freeSlots = new HashMap<>();
        for (StreckeTermin termin : futureTermine) {
            int used = (int) zuordnungRepository.countByTerminId(termin.getId());
            int free = termin.getMaxTeilnehmer() - used;
            if (free > 0) {
                freeSlots.put(termin.getId(), free);
            }
        }
        if (freeSlots.isEmpty()) {
            throw new IllegalArgumentException("Keine Termine mit freien Plätzen verfügbar.");
        }

        List<TerminSlot> slots = futureTermine.stream()
                .filter(t -> freeSlots.containsKey(t.getId()))
                .map(t -> new TerminSlot(t.getId(), t.getTerminDatum(), freeSlots.get(t.getId())))
                .sorted(Comparator.comparing(TerminSlot::datum))
                .toList();

        int assigned = 0;
        int notPlanned = 0;
        for (CarrierOverview row : unassigned) {
            LocalDate streckeBis = validUntil(row, AtemschutzFitnessType.STRECKEN);
            Long daysUntil = daysUntil(streckeBis, today);
            Long bestTerminId = pickTermin(slots, streckeBis, daysUntil);
            if (bestTerminId == null) {
                notPlanned++;
                continue;
            }
            StreckeTermin termin = futureTermine.stream()
                    .filter(t -> t.getId().equals(bestTerminId))
                    .findFirst()
                    .orElseThrow();
            StreckeZuordnung zuordnung = new StreckeZuordnung();
            zuordnung.setTermin(termin);
            zuordnung.setCarrier(row.carrier());
            zuordnungRepository.save(zuordnung);
            assigned++;
            for (TerminSlot slot : slots) {
                if (slot.id().equals(bestTerminId)) {
                    slot.decrementFree();
                    if (slot.free() <= 0) {
                        freeSlots.remove(bestTerminId);
                    }
                    break;
                }
            }
            slots = slots.stream().filter(s -> s.free() > 0).toList();
            if (slots.isEmpty()) {
                break;
            }
        }

        String message = assigned + " Geräteträger wurden automatisch zugeordnet.";
        if (notPlanned > 0) {
            message += " " + notPlanned + " Geräteträger konnten nicht verplant werden (keine freien Plätze).";
        }
        return new AutoAssignResult(assigned, notPlanned, message);
    }

    @Transactional
    public int clearAllAssignments(long unitId) {
        return zuordnungRepository.deleteAllByUnit(unitId, testModeService.isEnabled());
    }

    private List<CarrierOverview> loadUnassignedCarriers(long unitId) {
        boolean testData = testModeService.isEnabled();
        Map<Long, Long> assignedCarrierIds = zuordnungRepository.findByTerminIds(
                        terminRepository.findRecentByUnit(unitId, LocalDate.now().minusDays(30), testData).stream()
                                .map(StreckeTermin::getId)
                                .toList())
                .stream()
                .collect(Collectors.toMap(z -> z.getCarrier().getId(), z -> z.getTermin().getId(), (a, b) -> a));

        return atemschutzService.listCarrierOverviews(unitId, false, "all").carriers().stream()
                .filter(row -> row.carrier().getStatus() == AtemschutzCarrierStatus.ACTIVE)
                .filter(row -> !assignedCarrierIds.containsKey(row.carrier().getId()))
                .sorted(Comparator.comparing(row -> validUntil(row, AtemschutzFitnessType.STRECKEN),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Long pickTermin(List<TerminSlot> slots, LocalDate streckeBis, Long daysUntil) {
        if (slots.isEmpty()) {
            return null;
        }
        if (streckeBis == null || daysUntil == null || daysUntil < 0) {
            return slots.get(0).id();
        }
        for (int i = slots.size() - 1; i >= 0; i--) {
            TerminSlot slot = slots.get(i);
            if (slot.free() > 0 && !slot.datum().isAfter(streckeBis)) {
                return slot.id();
            }
        }
        return slots.stream().filter(s -> s.free() > 0).map(TerminSlot::id).findFirst().orElse(null);
    }

    private TerminView toTerminView(StreckeTermin termin, List<CarrierAssignmentView> teilnehmer, LocalDate today) {
        boolean vergangen = termin.getTerminDatum().isBefore(today);
        boolean voll = teilnehmer.size() >= termin.getMaxTeilnehmer();
        return new TerminView(
                termin.getId(),
                termin.getTerminDatum(),
                termin.getTerminZeit(),
                termin.getOrt(),
                termin.getMaxTeilnehmer(),
                teilnehmer.size(),
                termin.getBemerkung(),
                vergangen,
                voll,
                teilnehmer);
    }

    private CarrierAssignmentView toAssignmentView(
            StreckeZuordnung zuordnung,
            CarrierOverview overview,
            StreckeTermin termin,
            int warnDays,
            LocalDate today) {
        if (overview == null) {
            return null;
        }
        LocalDate streckeBis = validUntil(overview, AtemschutzFitnessType.STRECKEN);
        String dot = assignmentDot(streckeBis, termin.getTerminDatum());
        return new CarrierAssignmentView(
                overview.carrier().getId(),
                overview.carrier().getPerson().displayName(),
                streckeBis,
                validUntil(overview, AtemschutzFitnessType.G26_UNTERSUCHUNG),
                validUntil(overview, AtemschutzFitnessType.UEBUNG),
                daysUntil(streckeBis, today),
                dot);
    }

    private CarrierPoolView toPoolView(CarrierOverview overview, int warnDays, LocalDate today) {
        LocalDate streckeBis = validUntil(overview, AtemschutzFitnessType.STRECKEN);
        Long daysUntil = daysUntil(streckeBis, today);
        return new CarrierPoolView(
                overview.carrier().getId(),
                overview.carrier().getPerson().displayName(),
                streckeBis,
                validUntil(overview, AtemschutzFitnessType.G26_UNTERSUCHUNG),
                validUntil(overview, AtemschutzFitnessType.UEBUNG),
                daysUntil,
                poolDot(daysUntil, warnDays));
    }

    private static String poolDot(Long daysUntil, int warnDays) {
        if (daysUntil == null) {
            return "gruen";
        }
        if (daysUntil < 0) {
            return "rot";
        }
        if (daysUntil <= warnDays) {
            return "gelb";
        }
        return "gruen";
    }

    private static String assignmentDot(LocalDate streckeBis, LocalDate terminDatum) {
        if (streckeBis == null) {
            return "gruen";
        }
        return terminDatum.isAfter(streckeBis) ? "rot" : "gruen";
    }

    private static LocalDate validUntil(CarrierOverview overview, AtemschutzFitnessType type) {
        FitnessStatusView view = overview.summaries().get(type);
        return view != null ? view.validUntil() : null;
    }

    private static Long daysUntil(LocalDate validUntil, LocalDate today) {
        if (validUntil == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(today, validUntil);
    }

    private StreckeTermin requireTermin(long unitId, long terminId) {
        return terminRepository
                .findByIdAndUnit(terminId, unitId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Termin nicht gefunden."));
    }

    private AtemschutzCarrier requireActiveCarrier(long unitId, long carrierId) {
        AtemschutzCarrier carrier = carrierRepository
                .findByIdAndTestData(carrierId, testModeService.isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Geräteträger nicht gefunden."));
        if (carrier.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Geräteträger gehört nicht zu dieser Einheit.");
        }
        if (carrier.getStatus() != AtemschutzCarrierStatus.ACTIVE) {
            throw new IllegalArgumentException("Nur aktive Geräteträger können zugeordnet werden.");
        }
        return carrier;
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private static void validateTerminRequest(CreateTerminRequest request) {
        if (request.terminDatum() == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (request.maxTeilnehmer() < 1 || request.maxTeilnehmer() > 100) {
            throw new IllegalArgumentException("Max. Teilnehmer muss zwischen 1 und 100 liegen.");
        }
    }

    private static void validateTerminRequest(UpdateTerminRequest request) {
        validateTerminRequest(new CreateTerminRequest(
                request.terminDatum(), request.terminZeit(), request.ort(), request.maxTeilnehmer(), request.bemerkung()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class TerminSlot {
        private final Long id;
        private final LocalDate datum;
        private int free;

        private TerminSlot(Long id, LocalDate datum, int free) {
            this.id = id;
            this.datum = datum;
            this.free = free;
        }

        private Long id() {
            return id;
        }

        private LocalDate datum() {
            return datum;
        }

        private int free() {
            return free;
        }

        private void decrementFree() {
            free--;
        }
    }

    public record StreckePlanungView(
            List<TerminView> termine, List<CarrierPoolView> unassignedCarriers, int warnDays) {}

    public record TerminView(
            long id,
            LocalDate datum,
            LocalTime zeit,
            String ort,
            int maxTeilnehmer,
            int aktuelleTeilnehmer,
            String bemerkung,
            boolean vergangen,
            boolean voll,
            List<CarrierAssignmentView> teilnehmer) {}

    public record CarrierPoolView(
            long carrierId,
            String name,
            LocalDate streckeBis,
            LocalDate g26Bis,
            LocalDate uebungBis,
            Long daysUntilExpiry,
            String statusDot) {}

    public record CarrierAssignmentView(
            long carrierId,
            String name,
            LocalDate streckeBis,
            LocalDate g26Bis,
            LocalDate uebungBis,
            Long daysUntilExpiry,
            String statusDot) {}

    public record CreateTerminRequest(
            LocalDate terminDatum, LocalTime terminZeit, String ort, int maxTeilnehmer, String bemerkung) {}

    public record UpdateTerminRequest(
            LocalDate terminDatum, LocalTime terminZeit, String ort, int maxTeilnehmer, String bemerkung) {}

    public record AutoAssignResult(int assigned, int notPlanned, String message) {}
}
