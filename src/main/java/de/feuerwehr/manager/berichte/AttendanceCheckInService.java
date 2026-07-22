package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.DashboardTerminWidgetView;
import de.feuerwehr.manager.termine.UnitTermin;
import de.feuerwehr.manager.termine.UnitTerminRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceCheckInService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceReportRepository attendanceReportRepository;
    private final UnitTerminRepository unitTerminRepository;
    private final UserRepository userRepository;
    private final AnwesenheitslisteService anwesenheitslisteService;
    private final AnwesenheitslisteTerminSyncService terminSyncService;
    private final EinsatzberichtService einsatzberichtService;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<DashboardTerminWidgetView> enrichDashboardTermineForCheckIn(
            long unitId, List<DashboardTerminWidgetView> termine, boolean canWriteBerichte) {
        if (termine == null || termine.isEmpty()) {
            return List.of();
        }
        List<DashboardTerminWidgetView> result = new ArrayList<>();
        for (DashboardTerminWidgetView termin : termine) {
            if (!termin.today() || !termin.checkInAvailable() || !canWriteBerichte) {
                result.add(termin.withCheckIn(false, null));
                continue;
            }
            Optional<AttendanceReport> report = findDraftForTermin(unitId, termin.terminId());
            result.add(termin.withCheckIn(true, report.map(AttendanceReport::getId).orElse(null)));
        }
        return result;
    }

    @Transactional
    public AttendanceCheckInPageView openCheckIn(long unitId, long terminId, AppUserDetails actor) {
        UnitTermin termin = requireTermin(unitId, terminId);
        if (termin.getCategory() == null || !termin.getCategory().supportsAttendanceReports()) {
            throw new IllegalArgumentException("Für diesen Termin ist kein Check-In möglich.");
        }
        terminSyncService.syncSingleTermin(unitId, termin);
        AttendanceReport report = findDraftForTermin(unitId, terminId)
                .orElseGet(() -> {
                    anwesenheitslisteService.createDraftFromTerminIfMissing(unitId, termin);
                    return findDraftForTermin(unitId, terminId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Anwesenheitsliste für diesen Termin konnte nicht angelegt werden."));
                });
        if (report.getStatus() != IncidentReportStatus.ENTWURF) {
            throw new IllegalArgumentException("Check-In ist nur für Entwürfe möglich.");
        }
        claimCreatorIfMissing(report, actor);
        return toPageView(unitId, report, termin);
    }

    @Transactional(readOnly = true)
    public AttendanceCheckInPageView loadCheckIn(long unitId, long reportId) {
        AttendanceReport report = requireEditableReport(unitId, reportId);
        UnitTermin termin = report.getUnitTermin();
        if (termin == null) {
            throw new IllegalArgumentException("Diese Anwesenheitsliste ist keinem Termin zugeordnet.");
        }
        return toPageView(unitId, report, termin);
    }

    @Transactional
    public AttendanceCheckInPageView checkInPerson(long unitId, long reportId, long personId, AppUserDetails actor) {
        AttendanceReport report = requireEditableReport(unitId, reportId);
        claimCreatorIfMissing(report, actor);
        UnitTermin termin = requireLinkedTermin(report);
        Person person = requireAudiencePerson(unitId, termin, personId);
        LinkedHashSet<Long> checkedIn = new LinkedHashSet<>(checkedInPersonIds(report));
        checkedIn.add(person.getId());
        persistCheckedIn(report, unitId, checkedIn);
        return toPageView(unitId, report, termin);
    }

    @Transactional
    public AttendanceCheckInPageView checkOutPerson(long unitId, long reportId, long personId, AppUserDetails actor) {
        AttendanceReport report = requireEditableReport(unitId, reportId);
        claimCreatorIfMissing(report, actor);
        UnitTermin termin = requireLinkedTermin(report);
        LinkedHashSet<Long> checkedIn = new LinkedHashSet<>(checkedInPersonIds(report));
        checkedIn.remove(personId);
        persistCheckedIn(report, unitId, checkedIn);
        return toPageView(unitId, report, termin);
    }

    @Transactional
    public AttendanceCheckInPageView updateTheme(
            long unitId, long reportId, String theme, AppUserDetails actor) {
        if (theme == null || theme.isBlank()) {
            throw new IllegalArgumentException("Bitte ein Thema angeben.");
        }
        AttendanceReport report = requireEditableReport(unitId, reportId);
        claimCreatorIfMissing(report, actor);
        UnitTermin termin = requireLinkedTermin(report);
        String trimmed = theme.trim();
        if (trimmed.length() > 255) {
            trimmed = trimmed.substring(0, 255);
        }
        report.setTitle(trimmed);
        termin.setTitle(trimmed);
        unitTerminRepository.save(termin);
        attendanceReportRepository.save(report);
        return toPageView(unitId, report, termin);
    }

    @Transactional
    public long finishCheckIn(long unitId, long reportId, AppUserDetails actor) {
        AttendanceReport report = requireEditableReport(unitId, reportId);
        claimCreatorIfMissing(report, actor);
        attendanceReportRepository.save(report);
        return report.getId();
    }

    private AttendanceCheckInPageView toPageView(long unitId, AttendanceReport report, UnitTermin termin) {
        Set<Person> audience = anwesenheitslisteService.resolveAudiencePersonsPublic(unitId, termin);
        LinkedHashSet<Long> checkedInIds = new LinkedHashSet<>(checkedInPersonIds(report));
        List<AttendanceCheckInPageView.PersonTile> checkedIn = audience.stream()
                .filter(person -> checkedInIds.contains(person.getId()))
                .sorted(Comparator.comparing(Person::anwesenheitDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(person -> new AttendanceCheckInPageView.PersonTile(
                        person.getId(), person.anwesenheitDisplayName()))
                .toList();
        List<AttendanceCheckInPageView.PersonTile> available = audience.stream()
                .filter(person -> !checkedInIds.contains(person.getId()))
                .sorted(Comparator.comparing(Person::anwesenheitDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(person -> new AttendanceCheckInPageView.PersonTile(
                        person.getId(), person.anwesenheitDisplayName()))
                .toList();
        LocalTime start = report.getStartTime() != null
                ? report.getStartTime()
                : (termin.getStartAt() != null ? termin.getStartAt().toLocalTime() : null);
        return new AttendanceCheckInPageView(
                report.getId(),
                termin.getId(),
                unitId,
                report.getTitle() != null ? report.getTitle() : termin.getTitle(),
                termin.getCategory() != null ? termin.getCategory().displayLabel() : "",
                start != null ? TIME_FMT.format(start) : "",
                available,
                checkedIn);
    }

    private void persistCheckedIn(AttendanceReport report, long unitId, LinkedHashSet<Long> checkedInIds) {
        List<CrewAssignment> assignments =
                einsatzberichtService.parseCrewAssignments(report.getCrewAssignmentsJson());
        assignments = replaceBeteiligt(assignments, List.copyOf(checkedInIds));
        try {
            report.setCrewAssignmentsJson(objectMapper.writeValueAsString(assignments));
        } catch (Exception e) {
            throw new IllegalStateException("Check-In konnte nicht gespeichert werden.", e);
        }
        attendanceReportRepository.save(report);
        anwesenheitslisteService.saveCrewAsPersonnelPublic(report, assignments, unitId);
    }

    private static List<CrewAssignment> replaceBeteiligt(List<CrewAssignment> assignments, List<Long> personIds) {
        List<CrewAssignment> result = new ArrayList<>();
        boolean replaced = false;
        if (assignments != null) {
            for (CrewAssignment assignment : assignments) {
                if (assignment.vehicleId() == IncidentCrewSupport.BETEILIGT_VEHICLE_ID) {
                    result.add(new CrewAssignment(
                            IncidentCrewSupport.BETEILIGT_VEHICLE_ID,
                            personIds,
                            null,
                            null,
                            null,
                            true,
                            true));
                    replaced = true;
                } else {
                    result.add(assignment);
                }
            }
        }
        if (!replaced) {
            result.add(new CrewAssignment(
                    IncidentCrewSupport.BETEILIGT_VEHICLE_ID, personIds, null, null, null, true, true));
        }
        return result;
    }

    private List<Long> checkedInPersonIds(AttendanceReport report) {
        List<CrewAssignment> assignments =
                einsatzberichtService.parseCrewAssignments(report.getCrewAssignmentsJson());
        if (assignments == null) {
            return List.of();
        }
        for (CrewAssignment assignment : assignments) {
            if (assignment.vehicleId() == IncidentCrewSupport.BETEILIGT_VEHICLE_ID
                    && assignment.personIds() != null) {
                return assignment.personIds().stream().filter(Objects::nonNull).distinct().toList();
            }
        }
        return List.of();
    }

    private Optional<AttendanceReport> findDraftForTermin(long unitId, long terminId) {
        return attendanceReportRepository
                .findByUnitIdAndUnitTerminId(unitId, terminId, includeTestReports())
                .filter(report -> report.getStatus() == IncidentReportStatus.ENTWURF);
    }

    private AttendanceReport requireEditableReport(long unitId, long reportId) {
        AttendanceReport report = anwesenheitslisteService.requireReport(unitId, reportId);
        if (testModeService.isEnabled() && !report.isTestData()) {
            throw new IllegalArgumentException("Im Testmodus können nur Test-Anwesenheitslisten bearbeitet werden.");
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF) {
            throw new IllegalArgumentException("Check-In ist nur für Entwürfe möglich.");
        }
        if (report.getUnitTermin() == null) {
            throw new IllegalArgumentException("Diese Anwesenheitsliste ist keinem Termin zugeordnet.");
        }
        return report;
    }

    private UnitTermin requireTermin(long unitId, long terminId) {
        UnitTermin termin = unitTerminRepository
                .findByIdAndUnitId(terminId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Termin nicht gefunden."));
        if (!termin.isAudienceAll()) {
            termin.getAssignedPersons().size();
            termin.getAssignedGroups().forEach(g -> g.getMembers().size());
        }
        return termin;
    }

    private UnitTermin requireLinkedTermin(AttendanceReport report) {
        UnitTermin termin = report.getUnitTermin();
        if (termin == null) {
            throw new IllegalArgumentException("Diese Anwesenheitsliste ist keinem Termin zugeordnet.");
        }
        if (!termin.isAudienceAll()) {
            termin.getAssignedPersons().size();
            termin.getAssignedGroups().forEach(g -> g.getMembers().size());
        }
        return termin;
    }

    private Person requireAudiencePerson(long unitId, UnitTermin termin, long personId) {
        Set<Person> audience = anwesenheitslisteService.resolveAudiencePersonsPublic(unitId, termin);
        return audience.stream()
                .filter(person -> Objects.equals(person.getId(), personId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Person gehört nicht zur Zielgruppe dieses Termins."));
    }

    private void claimCreatorIfMissing(AttendanceReport report, AppUserDetails actor) {
        if (report.getCreatedByUser() != null || actor == null) {
            return;
        }
        User user = userRepository.findById(actor.getUserId()).orElse(null);
        report.setCreatedByUser(user);
        report.setCreatedByName(actor.getDisplayName());
        attendanceReportRepository.save(report);
    }

    private boolean includeTestReports() {
        return testModeService.isEnabled();
    }
}
