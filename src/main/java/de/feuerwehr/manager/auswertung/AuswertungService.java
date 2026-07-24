package de.feuerwehr.manager.auswertung;

import de.feuerwehr.manager.berichte.AttendancePersonStatus;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportPersonnel;
import de.feuerwehr.manager.berichte.AttendanceReportPersonnelRepository;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.CrewAssignment;
import de.feuerwehr.manager.berichte.CustomDeployedEquipment;
import de.feuerwehr.manager.berichte.DeployedEquipmentAssignment;
import de.feuerwehr.manager.berichte.EinsatzberichtService;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportEquipment;
import de.feuerwehr.manager.berichte.IncidentReportEquipmentRepository;
import de.feuerwehr.manager.berichte.IncidentReportPersonnel;
import de.feuerwehr.manager.berichte.IncidentReportPersonnelRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.berichte.IncidentReportVehicle;
import de.feuerwehr.manager.berichte.IncidentReportVehicleRepository;
import de.feuerwehr.manager.berichte.IncidentVehicleCrewRole;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.termine.TermineCategory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuswertungService {

    private static final DateTimeFormatter DATE_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AttendanceReportRepository attendanceReportRepository;
    private final AttendanceReportPersonnelRepository attendanceReportPersonnelRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final IncidentReportVehicleRepository incidentReportVehicleRepository;
    private final IncidentReportEquipmentRepository incidentReportEquipmentRepository;
    private final PersonRepository personRepository;
    private final VehicleRepository vehicleRepository;
    private final EinsatzberichtService einsatzberichtService;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public AuswertungOverview overview(long unitId, AuswertungFilter filter) {
        List<PersonStatRow> personen = personStats(unitId, filter);
        EventStatSummary events = eventStats(unitId, filter);
        List<ChartSlice> topPersonen = personen.stream()
                .limit(10)
                .map(p -> new ChartSlice(p.displayName(), p.teilnahmen()))
                .toList();
        return new AuswertungOverview(
                events.anzahlEinsaetze(),
                events.anzahlUebungen(),
                events.anzahlSonstiges(),
                (int) personen.stream().filter(p -> p.teilnahmen() > 0).count(),
                personen.stream().mapToDouble(PersonStatRow::stunden).sum(),
                topPersonen,
                events.stichworte().stream().limit(8).toList());
    }

    @Transactional(readOnly = true)
    public List<PersonStatRow> personStats(long unitId, AuswertungFilter filter) {
        boolean includeTest = testModeService.isEnabled();
        Map<Long, MutablePersonStat> stats = new HashMap<>();
        Map<Long, String> names = new HashMap<>();

        for (Person person : personRepository.findActiveByUnitId(unitId, includeTest)) {
            names.put(person.getId(), person.anwesenheitDisplayName());
            stats.put(person.getId(), new MutablePersonStat());
        }

        int basisEvents = 0;

        if (includesIncidents(filter.typ())) {
            List<IncidentReport> incidents = loadIncidents(unitId, filter);
            basisEvents += incidents.size();
            for (IncidentReport report : incidents) {
                if (!matchesTime(report.getAlarmTime(), filter)) {
                    continue;
                }
                double hours = durationHours(report.getAlarmTime(), report.getEndTime());
                List<IncidentReportPersonnel> personnel =
                        incidentReportPersonnelRepository.findByIncidentReportId(report.getId());
                Set<Long> counted = new HashSet<>();
                for (IncidentReportPersonnel row : personnel) {
                    Long personId = row.getPerson() != null ? row.getPerson().getId() : null;
                    if (personId == null || !counted.add(personId)) {
                        continue;
                    }
                    if (filter.personId() != null && !filter.personId().equals(personId)) {
                        continue;
                    }
                    IncidentReportVehicle vehicle = row.getIncidentReportVehicle();
                    long vehicleId = 0L;
                    String vehicleName = null;
                    if (vehicle != null) {
                        vehicleId = vehicle.getVehicle() != null ? vehicle.getVehicle().getId() : 0L;
                        vehicleName = vehicle.getVehicleName() != null
                                ? vehicle.getVehicleName()
                                : (vehicle.getVehicle() != null ? vehicle.getVehicle().getName() : "Fahrzeug");
                    }
                    if (filter.vehicleId() != null
                            && (vehicleId <= 0 || !filter.vehicleId().equals(vehicleId))) {
                        continue;
                    }
                    MutablePersonStat stat = stats.computeIfAbsent(personId, ignored -> new MutablePersonStat());
                    names.putIfAbsent(personId, displayName(row.getDisplayName(), personId));
                    stat.einsaetze++;
                    stat.stunden += hours;
                    stat.touch(report.getIncidentDate());
                    if (row.getVehicleRole() == IncidentVehicleCrewRole.MASCHINIST) {
                        stat.maschinist++;
                    }
                    if (row.getVehicleRole() == IncidentVehicleCrewRole.EINHEITSFUEHRER) {
                        stat.einheitsfuehrer++;
                    }
                    if (vehicleId > 0 && vehicleName != null) {
                        stat.fahrzeuge.merge(vehicleId + "|" + vehicleName, 1, Integer::sum);
                    }
                }
            }
        }

        if (includesAttendance(filter.typ())) {
            List<AttendanceReport> attendances = loadAttendances(unitId, filter);
            for (AttendanceReport report : attendances) {
                if (!matchesAttendanceCategory(report, filter.typ())) {
                    continue;
                }
                if (!matchesThema(report, filter.thema())) {
                    continue;
                }
                if (!matchesTime(report.getStartTime(), filter)) {
                    continue;
                }
                basisEvents++;
                double hours = durationHours(report.getStartTime(), report.getEndTime());
                boolean sonstiges = isSonstiges(report);
                Map<Long, Long> personVehicle = personVehicleFromCrew(report.getCrewAssignmentsJson());
                Map<Long, IncidentVehicleCrewRole> roles = rolesFromCrew(report.getCrewAssignmentsJson());

                List<AttendanceReportPersonnel> personnel =
                        attendanceReportPersonnelRepository.findByReportId(report.getId());
                for (AttendanceReportPersonnel row : personnel) {
                    if (row.getAttendanceStatus() != AttendancePersonStatus.PRESENT) {
                        continue;
                    }
                    Long personId = row.getPerson() != null ? row.getPerson().getId() : null;
                    if (personId == null) {
                        continue;
                    }
                    if (filter.personId() != null && !filter.personId().equals(personId)) {
                        continue;
                    }
                    Long vehicleId = personVehicle.get(personId);
                    if (filter.vehicleId() != null
                            && (vehicleId == null || !filter.vehicleId().equals(vehicleId))) {
                        continue;
                    }
                    MutablePersonStat stat = stats.computeIfAbsent(personId, ignored -> new MutablePersonStat());
                    names.putIfAbsent(personId, displayName(row.getDisplayName(), personId));
                    if (sonstiges) {
                        stat.sonstiges++;
                    } else {
                        stat.uebungen++;
                    }
                    stat.stunden += hours;
                    stat.touch(report.getEventDate());
                    IncidentVehicleCrewRole role = roles.get(personId);
                    if (role == IncidentVehicleCrewRole.MASCHINIST) {
                        stat.maschinist++;
                    }
                    if (role == IncidentVehicleCrewRole.EINHEITSFUEHRER) {
                        stat.einheitsfuehrer++;
                    }
                    if (vehicleId != null && vehicleId > 0) {
                        String vehicleName = vehicleName(unitId, vehicleId);
                        stat.fahrzeuge.merge(vehicleId + "|" + vehicleName, 1, Integer::sum);
                    }
                }
            }
        }

        int finalBasis = Math.max(basisEvents, 1);
        List<PersonStatRow> rows = new ArrayList<>();
        for (Map.Entry<Long, MutablePersonStat> entry : stats.entrySet()) {
            MutablePersonStat stat = entry.getValue();
            int teilnahmen = switch (filter.typ()) {
                case EINSAETZE -> stat.einsaetze;
                case UEBUNGEN -> stat.uebungen;
                case SONSTIGES -> stat.sonstiges;
                case BEIDES -> stat.einsaetze + stat.uebungen;
            };
            if (filter.personId() == null && teilnahmen <= 0) {
                continue;
            }
            if (filter.personId() != null && !filter.personId().equals(entry.getKey()) && teilnahmen <= 0) {
                continue;
            }
            List<ChartSlice> vehicles = stat.fahrzeuge.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(8)
                    .map(e -> {
                        String label = e.getKey();
                        int sep = label.indexOf('|');
                        return new ChartSlice(sep >= 0 ? label.substring(sep + 1) : label, e.getValue());
                    })
                    .toList();
            double quote = Math.round((teilnahmen * 1000.0) / finalBasis) / 10.0;
            rows.add(new PersonStatRow(
                    entry.getKey(),
                    names.getOrDefault(entry.getKey(), "Person #" + entry.getKey()),
                    stat.einsaetze,
                    stat.uebungen,
                    stat.sonstiges,
                    teilnahmen,
                    round1(stat.stunden),
                    Math.max(0, stat.maschinist),
                    Math.max(0, stat.einheitsfuehrer),
                    stat.letzte != null ? DATE_DE.format(stat.letzte) : "—",
                    quote,
                    vehicles));
        }
        rows.sort(Comparator.comparingInt(PersonStatRow::teilnahmen).reversed()
                .thenComparing(PersonStatRow::displayName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public EventStatSummary eventStats(long unitId, AuswertungFilter filter) {
        int einsaetze = 0;
        int uebungen = 0;
        int sonstiges = 0;
        double stunden = 0;
        int personSum = 0;
        int eventWithPeople = 0;
        Map<String, Integer> stichworte = new HashMap<>();
        Map<String, Integer> themen = new HashMap<>();

        if (includesIncidents(filter.typ())) {
            for (IncidentReport report : loadIncidents(unitId, filter)) {
                if (!matchesTime(report.getAlarmTime(), filter)) {
                    continue;
                }
                einsaetze++;
                stunden += durationHours(report.getAlarmTime(), report.getEndTime());
                String sw = trimToNull(report.getStichwort());
                if (sw != null) {
                    stichworte.merge(sw, 1, Integer::sum);
                }
                int people = incidentReportPersonnelRepository.findByIncidentReportId(report.getId()).size();
                if (people > 0) {
                    personSum += people;
                    eventWithPeople++;
                }
            }
        }

        if (includesAttendance(filter.typ())) {
            for (AttendanceReport report : loadAttendances(unitId, filter)) {
                if (!matchesAttendanceCategory(report, filter.typ())) {
                    continue;
                }
                if (!matchesThema(report, filter.thema())) {
                    continue;
                }
                if (!matchesTime(report.getStartTime(), filter)) {
                    continue;
                }
                if (isSonstiges(report)) {
                    sonstiges++;
                } else {
                    uebungen++;
                }
                stunden += durationHours(report.getStartTime(), report.getEndTime());
                String thema = trimToNull(report.getTitle());
                if (thema != null) {
                    themen.merge(thema, 1, Integer::sum);
                }
                long present = attendanceReportPersonnelRepository.findByReportId(report.getId()).stream()
                        .filter(p -> p.getAttendanceStatus() == AttendancePersonStatus.PRESENT)
                        .count();
                if (present > 0) {
                    personSum += (int) present;
                    eventWithPeople++;
                }
            }
        }

        int gesamt = switch (filter.typ()) {
            case EINSAETZE -> einsaetze;
            case UEBUNGEN -> uebungen;
            case SONSTIGES -> sonstiges;
            case BEIDES -> einsaetze + uebungen;
        };
        double avg = eventWithPeople > 0 ? round1(personSum / (double) eventWithPeople) : 0;
        return new EventStatSummary(
                einsaetze,
                uebungen,
                sonstiges,
                gesamt,
                round1(stunden),
                avg,
                toTopSlices(stichworte, 12),
                toTopSlices(themen, 12));
    }

    @Transactional(readOnly = true)
    public List<VehicleStatRow> vehicleStats(long unitId, AuswertungFilter filter) {
        Map<Long, MutableVehicleStat> stats = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Vehicle vehicle : vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(
                unitId, testModeService.isEnabled())) {
            names.put(vehicle.getId(), vehicle.getName());
            stats.put(vehicle.getId(), new MutableVehicleStat());
        }

        if (includesIncidents(filter.typ())) {
            for (IncidentReport report : loadIncidents(unitId, filter)) {
                if (!matchesTime(report.getAlarmTime(), filter)) {
                    continue;
                }
                List<IncidentReportVehicle> vehicles =
                        incidentReportVehicleRepository.findByIncidentReportId(report.getId());
                List<IncidentReportPersonnel> personnel =
                        incidentReportPersonnelRepository.findByIncidentReportId(report.getId());
                for (IncidentReportVehicle vehicleRow : vehicles) {
                    if (!vehicleRow.isInvolved()) {
                        continue;
                    }
                    Long vehicleId = vehicleRow.getVehicle() != null ? vehicleRow.getVehicle().getId() : null;
                    if (vehicleId == null) {
                        continue;
                    }
                    if (filter.vehicleId() != null && !filter.vehicleId().equals(vehicleId)) {
                        continue;
                    }
                    MutableVehicleStat stat = stats.computeIfAbsent(vehicleId, ignored -> new MutableVehicleStat());
                    names.putIfAbsent(vehicleId, vehicleRow.getVehicleName());
                    stat.einsaetze++;
                    int crew = 0;
                    for (IncidentReportPersonnel person : personnel) {
                        if (person.getIncidentReportVehicle() != null
                                && Objects.equals(person.getIncidentReportVehicle().getId(), vehicleRow.getId())) {
                            crew++;
                            if (person.getVehicleRole() == IncidentVehicleCrewRole.MASCHINIST) {
                                stat.maschinist++;
                            }
                            if (person.getVehicleRole() == IncidentVehicleCrewRole.EINHEITSFUEHRER) {
                                stat.ef++;
                            }
                        }
                    }
                    stat.besatzungSum += crew;
                    stat.besatzungCount++;
                }
            }
        }

        if (includesAttendance(filter.typ())) {
            for (AttendanceReport report : loadAttendances(unitId, filter)) {
                if (!matchesAttendanceCategory(report, filter.typ())) {
                    continue;
                }
                if (!matchesThema(report, filter.thema())) {
                    continue;
                }
                if (!matchesTime(report.getStartTime(), filter)) {
                    continue;
                }
                boolean sonstiges = isSonstiges(report);
                List<CrewAssignment> assignments =
                        einsatzberichtService.parseCrewAssignments(report.getCrewAssignmentsJson());
                for (CrewAssignment assignment : assignments) {
                    long vehicleId = assignment.vehicleId();
                    if (vehicleId <= 0) {
                        continue;
                    }
                    if (filter.vehicleId() != null && !filter.vehicleId().equals(vehicleId)) {
                        continue;
                    }
                    MutableVehicleStat stat = stats.computeIfAbsent(vehicleId, ignored -> new MutableVehicleStat());
                    names.putIfAbsent(vehicleId, vehicleName(unitId, vehicleId));
                    if (sonstiges) {
                        stat.sonstiges++;
                    } else {
                        stat.uebungen++;
                    }
                    int crew = assignment.personIds() != null ? assignment.personIds().size() : 0;
                    stat.besatzungSum += crew;
                    stat.besatzungCount++;
                    if (assignment.maschinistPersonId() != null) {
                        stat.maschinist++;
                    }
                    if (assignment.einheitsfuehrerPersonId() != null) {
                        stat.ef++;
                    }
                }
            }
        }

        List<VehicleStatRow> rows = new ArrayList<>();
        for (Map.Entry<Long, MutableVehicleStat> entry : stats.entrySet()) {
            MutableVehicleStat stat = entry.getValue();
            int gesamt = switch (filter.typ()) {
                case EINSAETZE -> stat.einsaetze;
                case UEBUNGEN -> stat.uebungen;
                case SONSTIGES -> stat.sonstiges;
                case BEIDES -> stat.einsaetze + stat.uebungen;
            };
            if (gesamt <= 0) {
                continue;
            }
            double avg = stat.besatzungCount > 0 ? round1(stat.besatzungSum / (double) stat.besatzungCount) : 0;
            rows.add(new VehicleStatRow(
                    entry.getKey(),
                    names.getOrDefault(entry.getKey(), "Fahrzeug #" + entry.getKey()),
                    stat.einsaetze,
                    stat.uebungen,
                    stat.sonstiges,
                    gesamt,
                    avg,
                    stat.maschinist,
                    stat.ef));
        }
        rows.sort(Comparator.comparingInt(VehicleStatRow::gesamt).reversed()
                .thenComparing(VehicleStatRow::vehicleName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<EquipmentStatRow> equipmentStats(long unitId, AuswertungFilter filter) {
        Map<String, MutableEquipmentStat> stats = new LinkedHashMap<>();

        if (includesIncidents(filter.typ()) || filter.typ() == AuswertungTypFilter.BEIDES
                || filter.typ() == AuswertungTypFilter.EINSAETZE) {
            for (IncidentReport report : loadIncidents(unitId, filter)) {
                if (!matchesTime(report.getAlarmTime(), filter)) {
                    continue;
                }
                for (IncidentReportEquipment row :
                        incidentReportEquipmentRepository.findByIncidentReportId(report.getId())) {
                    if (filter.vehicleId() != null
                            && (row.getVehicle() == null
                                    || !filter.vehicleId().equals(row.getVehicle().getId()))) {
                        continue;
                    }
                    String name = trimToNull(row.getEquipmentName());
                    if (name == null) {
                        continue;
                    }
                    String key = name.toLowerCase(Locale.GERMAN);
                    MutableEquipmentStat stat = stats.computeIfAbsent(key, ignored -> new MutableEquipmentStat(name,
                            row.getCategoryName()));
                    stat.anzahl++;
                    if (row.getVehicle() != null) {
                        stat.vehicles.merge(row.getVehicle().getName(), 1, Integer::sum);
                    }
                }
            }
        }

        if (includesAttendance(filter.typ())) {
            for (AttendanceReport report : loadAttendances(unitId, filter)) {
                if (!matchesAttendanceCategory(report, filter.typ())) {
                    continue;
                }
                if (!matchesThema(report, filter.thema())) {
                    continue;
                }
                if (!matchesTime(report.getStartTime(), filter)) {
                    continue;
                }
                List<DeployedEquipmentAssignment> assignments =
                        einsatzberichtService.parseDeployedEquipment(report.getDeployedEquipmentJson());
                for (DeployedEquipmentAssignment assignment : assignments) {
                    if (filter.vehicleId() != null && !filter.vehicleId().equals(assignment.vehicleId())) {
                        continue;
                    }
                    String vehicleName = vehicleName(unitId, assignment.vehicleId());
                    if (assignment.customEquipment() != null) {
                        for (CustomDeployedEquipment custom : assignment.customEquipment()) {
                            if (custom == null || custom.name() == null || custom.name().isBlank()) {
                                continue;
                            }
                            String name = custom.name().trim();
                            String key = name.toLowerCase(Locale.GERMAN);
                            MutableEquipmentStat stat = stats.computeIfAbsent(
                                    key, ignored -> new MutableEquipmentStat(name, custom.categoryName()));
                            stat.anzahl++;
                            stat.vehicles.merge(vehicleName, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        List<EquipmentStatRow> rows = new ArrayList<>();
        for (MutableEquipmentStat stat : stats.values()) {
            rows.add(new EquipmentStatRow(
                    stat.name,
                    stat.category != null ? stat.category : "—",
                    stat.anzahl,
                    toTopSlices(stat.vehicles, 6)));
        }
        rows.sort(Comparator.comparingInt(EquipmentStatRow::anzahl).reversed()
                .thenComparing(EquipmentStatRow::equipmentName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PersonOption> personOptions(long unitId) {
        return personRepository.findActiveByUnitId(unitId, testModeService.isEnabled()).stream()
                .map(p -> new PersonOption(p.getId(), p.anwesenheitDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleOption> vehicleOptions(long unitId) {
        return vehicleRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.isEnabled())
                .stream()
                .map(v -> new VehicleOption(v.getId(), v.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> themaOptions(long unitId, AuswertungFilter filter) {
        Set<String> themen = new HashSet<>();
        for (AttendanceReport report : loadAttendances(unitId, filter)) {
            if (isSonstiges(report)) {
                continue;
            }
            String title = trimToNull(report.getTitle());
            if (title != null) {
                themen.add(title);
            }
        }
        return themen.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Transactional(readOnly = true)
    public List<String> stichwortOptions(long unitId, AuswertungFilter filter) {
        Set<String> values = new HashSet<>();
        for (IncidentReport report : loadIncidents(unitId, filter)) {
            String sw = trimToNull(report.getStichwort());
            if (sw != null) {
                values.add(sw);
            }
        }
        return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public record PersonOption(long id, String name) {}

    public record VehicleOption(long id, String name) {}

    private List<IncidentReport> loadIncidents(long unitId, AuswertungFilter filter) {
        List<IncidentReport> reports = incidentReportRepository.findByUnitIdAndDateRange(
                unitId, filter.from(), filter.to(), testModeService.isEnabled());
        String stichwort = filter.stichwort() != null ? filter.stichwort().trim() : "";
        List<IncidentReport> filtered = new ArrayList<>();
        for (IncidentReport report : reports) {
            if (report.getStatus() == IncidentReportStatus.ENTWURF) {
                continue;
            }
            if (!stichwort.isEmpty() && !stichwort.equalsIgnoreCase(nullToEmpty(report.getStichwort()))) {
                continue;
            }
            filtered.add(report);
        }
        return filtered;
    }

    private List<AttendanceReport> loadAttendances(long unitId, AuswertungFilter filter) {
        List<AttendanceReport> reports = attendanceReportRepository.findByUnitIdAndDateRange(
                unitId, filter.from(), filter.to(), testModeService.isEnabled());
        List<AttendanceReport> filtered = new ArrayList<>();
        for (AttendanceReport report : reports) {
            if (report.getStatus() == IncidentReportStatus.ENTWURF) {
                continue;
            }
            filtered.add(report);
        }
        return filtered;
    }

    private boolean includesIncidents(AuswertungTypFilter typ) {
        return typ == AuswertungTypFilter.BEIDES || typ == AuswertungTypFilter.EINSAETZE;
    }

    private boolean includesAttendance(AuswertungTypFilter typ) {
        return typ == AuswertungTypFilter.BEIDES
                || typ == AuswertungTypFilter.UEBUNGEN
                || typ == AuswertungTypFilter.SONSTIGES;
    }

    private boolean matchesAttendanceCategory(AttendanceReport report, AuswertungTypFilter typ) {
        boolean sonstiges = isSonstiges(report);
        return switch (typ) {
            case EINSAETZE -> false;
            case UEBUNGEN -> !sonstiges;
            case SONSTIGES -> sonstiges;
            case BEIDES -> !sonstiges;
        };
    }

    private boolean isSonstiges(AttendanceReport report) {
        return report.getTerminCategory() == TermineCategory.SONSTIGES;
    }

    private boolean matchesThema(AttendanceReport report, String thema) {
        if (thema == null || thema.isBlank()) {
            return true;
        }
        return thema.equalsIgnoreCase(nullToEmpty(report.getTitle()));
    }

    private boolean matchesTime(LocalTime start, AuswertungFilter filter) {
        if (!filter.hasTimeFilter()) {
            return true;
        }
        if (start == null) {
            return false;
        }
        if (filter.timeFrom() != null && start.isBefore(filter.timeFrom())) {
            return false;
        }
        return filter.timeTo() == null || !start.isAfter(filter.timeTo());
    }

    private Map<Long, Long> personVehicleFromCrew(String json) {
        Map<Long, Long> result = new HashMap<>();
        for (CrewAssignment assignment : einsatzberichtService.parseCrewAssignments(json)) {
            if (assignment.personIds() == null) {
                continue;
            }
            for (Long personId : assignment.personIds()) {
                if (personId != null) {
                    result.put(personId, assignment.vehicleId());
                }
            }
            if (assignment.maschinistPersonId() != null) {
                result.putIfAbsent(assignment.maschinistPersonId(), assignment.vehicleId());
            }
            if (assignment.einheitsfuehrerPersonId() != null) {
                result.putIfAbsent(assignment.einheitsfuehrerPersonId(), assignment.vehicleId());
            }
        }
        return result;
    }

    private Map<Long, IncidentVehicleCrewRole> rolesFromCrew(String json) {
        Map<Long, IncidentVehicleCrewRole> result = new HashMap<>();
        for (CrewAssignment assignment : einsatzberichtService.parseCrewAssignments(json)) {
            if (assignment.maschinistPersonId() != null) {
                result.put(assignment.maschinistPersonId(), IncidentVehicleCrewRole.MASCHINIST);
            }
            if (assignment.einheitsfuehrerPersonId() != null) {
                result.put(assignment.einheitsfuehrerPersonId(), IncidentVehicleCrewRole.EINHEITSFUEHRER);
            }
        }
        return result;
    }

    private String vehicleName(long unitId, long vehicleId) {
        return vehicleRepository
                .findById(vehicleId)
                .filter(v -> v.getUnit() != null && Objects.equals(v.getUnit().getId(), unitId))
                .map(Vehicle::getName)
                .orElse("Fahrzeug #" + vehicleId);
    }

    private static List<ChartSlice> toTopSlices(Map<String, Integer> source, int limit) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(e -> new ChartSlice(e.getKey(), e.getValue()))
                .toList();
    }

    private static double durationHours(LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(from, to);
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return minutes / 60.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String displayName(String fallback, long personId) {
        String name = trimToNull(fallback);
        return name != null ? name : ("Person #" + personId);
    }

    private static final class MutablePersonStat {
        int einsaetze;
        int uebungen;
        int sonstiges;
        double stunden;
        int maschinist;
        int einheitsfuehrer;
        LocalDate letzte;
        final Map<String, Integer> fahrzeuge = new HashMap<>();

        void touch(LocalDate date) {
            if (date == null) {
                return;
            }
            if (letzte == null || date.isAfter(letzte)) {
                letzte = date;
            }
        }
    }

    private static final class MutableVehicleStat {
        int einsaetze;
        int uebungen;
        int sonstiges;
        int besatzungSum;
        int besatzungCount;
        int maschinist;
        int ef;
    }

    private static final class MutableEquipmentStat {
        final String name;
        final String category;
        int anzahl;
        final Map<String, Integer> vehicles = new HashMap<>();

        MutableEquipmentStat(String name, String category) {
            this.name = name;
            this.category = category;
        }
    }
}
