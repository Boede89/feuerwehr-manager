package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.TermineCategory;
import de.feuerwehr.manager.termine.UnitTermin;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnwesenheitslisteService {

    private static final TypeReference<List<PersonnelPayload>> PERSONNEL_LIST = new TypeReference<>() {};

    private final AttendanceReportRepository attendanceReportRepository;
    private final AttendanceReportPersonnelRepository attendanceReportPersonnelRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final PersonalService personalService;
    private final TestModeService testModeService;
    private final EinsatzberichtService einsatzberichtService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AnwesenheitslisteListResponse listForYear(long unitId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = yearStart.plusYears(1);
        List<AttendanceReport> reports = attendanceReportRepository.findByUnitIdAndYear(
                unitId, yearStart, yearEnd, includeTestReports());
        List<AnwesenheitslisteListItemView> items = reports.stream().map(this::toListItem).toList();
        return new AnwesenheitslisteListResponse(items);
    }

    @Transactional(readOnly = true)
    public AttendanceReport requireReport(long unitId, long reportId) {
        return attendanceReportRepository
                .findByIdAndUnitId(reportId, unitId, includeTestReports())
                .orElseThrow(() -> new IllegalArgumentException("Anwesenheitsliste nicht gefunden."));
    }

    @Transactional(readOnly = true)
    public List<AttendanceReportPersonnel> listPersonnel(long reportId) {
        return attendanceReportPersonnelRepository.findByReportId(reportId);
    }

    @Transactional(readOnly = true)
    public String buildUnitPersonsJson(long unitId) {
        List<Map<String, Object>> items = personalService.listPersons(unitId).stream()
                .map(person -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", person.getId());
                    item.put("name", person.anwesenheitDisplayName());
                    return item;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Transactional(readOnly = true)
    public String buildPersonnelJson(long reportId) {
        List<PersonnelPayload> payloads = listPersonnel(reportId).stream()
                .map(row -> new PersonnelPayload(
                        row.getPerson() != null ? row.getPerson().getId() : null,
                        row.getDisplayName(),
                        row.getAttendanceStatus().name()))
                .toList();
        try {
            return objectMapper.writeValueAsString(payloads);
        } catch (Exception e) {
            return "[]";
        }
    }

    public EinsatzberichtForm newEinsatzForm(long unitId) {
        EinsatzberichtForm form = einsatzberichtService.newForm(unitId);
        form.setIncidentNumber(suggestReportNumber(unitId, form.getIncidentDate()));
        form.setStichwort("");
        return form;
    }

    @Transactional(readOnly = true)
    public List<String> listKnownStichworte(long unitId) {
        return attendanceReportRepository.findDistinctTitlesByUnit(unitId, includeTestReports());
    }

    @Transactional(readOnly = true)
    public KraefteFahrzeugeState buildKraefteFahrzeugeState(long unitId, Long attendanceReportId) {
        if (attendanceReportId == null) {
            return einsatzberichtService.buildKraefteFahrzeugeState(unitId, null);
        }
        AttendanceReport report = requireReport(unitId, attendanceReportId);
        List<Long> anwesendPersonIds = listPersonnel(attendanceReportId).stream()
                .map(AttendanceReportPersonnel::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .distinct()
                .toList();
        Set<Long> manualPoolPersonIds = null;
        if (report.getUnitTermin() != null) {
            UnitTermin termin = report.getUnitTermin();
            touchTerminAudience(termin);
            manualPoolPersonIds = resolveAudiencePersons(unitId, termin).stream()
                    .map(Person::getId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        return einsatzberichtService.buildKraefteFahrzeugeStateForAnwesenheit(
                unitId, anwesendPersonIds, manualPoolPersonIds);
    }

    @Transactional(readOnly = true)
    public String buildUnitAddressJson(long unitId) {
        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        try {
            return objectMapper.writeValueAsString(UnitAddressSupport.fromUnit(unit));
        } catch (Exception e) {
            return "{}";
        }
    }

    @Transactional
    public AnwesenheitFormBundle buildFormBundle(long unitId, Long reportId) {
        AttendanceReport report = reportId != null ? requireReport(unitId, reportId) : null;
        if (report != null) {
            clearLegacyTerminPersonnelPreload(report, unitId);
        }
        EinsatzberichtForm form =
                report != null ? AnwesenheitslisteEinsatzFormBridge.toEinsatzForm(report) : newEinsatzForm(unitId);
        if (report != null) {
            enrichEinsatzFormFromTermin(report, form);
        }
        unitRepository.findById(unitId).ifPresent(unit -> UnitAddressSupport.applyDefaultsToForm(form, unit));
        KraefteFahrzeugeState kraefteState = buildKraefteFahrzeugeState(unitId, reportId);
        if (form.getCrewAssignmentsJson() == null || form.getCrewAssignmentsJson().isBlank()) {
            if (reportId != null) {
                form.setCrewAssignmentsJson(buildCrewJsonFromPersonnel(unitId, reportId));
            } else {
                form.setCrewAssignmentsJson(KraefteCrewJsonSupport.buildCrewJson(kraefteState));
            }
        }
        if (form.getDeployedEquipmentJson() == null || form.getDeployedEquipmentJson().isBlank()) {
            form.setDeployedEquipmentJson("[]");
        }
        if (form.getPersonDamageDetailsJson() == null || form.getPersonDamageDetailsJson().isBlank()) {
            form.setPersonDamageDetailsJson(PersonDamageDetailsSupport.emptyJson());
        }
        if (form.getDamagePerpetratorJson() == null || form.getDamagePerpetratorJson().isBlank()) {
            form.setDamagePerpetratorJson(DamagePerpetratorSupport.emptyJson());
        }
        return new AnwesenheitFormBundle(
                report,
                form,
                kraefteState,
                einsatzberichtService.serializeKraefteFahrzeugeState(kraefteState),
                einsatzberichtService.listPersonsForForm(unitId),
                listKnownStichworte(unitId),
                einsatzberichtService.isForeignUnitPersonnelAllowed(unitId));
    }

    private void enrichEinsatzFormFromTermin(AttendanceReport report, EinsatzberichtForm form) {
        if (report == null || form == null || report.getUnitTermin() == null) {
            return;
        }
        if (form.getIncidentCommander() != null && !form.getIncidentCommander().isBlank()) {
            return;
        }
        if (report.getInstructorResponsible() != null && !report.getInstructorResponsible().isBlank()) {
            form.setIncidentCommander(report.getInstructorResponsible());
            return;
        }
        UnitTermin termin = report.getUnitTermin();
        termin.getInstructorPersons().size();
        String instructors = formatInstructorNames(termin);
        if (instructors != null && !instructors.isBlank()) {
            form.setIncidentCommander(instructors);
        }
    }

    @Transactional(readOnly = true)
    public String buildCrewJsonFromPersonnel(long unitId, long attendanceReportId) {
        List<Long> personIds = listPersonnel(attendanceReportId).stream()
                .map(AttendanceReportPersonnel::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .distinct()
                .toList();
        if (personIds.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[{\"vehicleId\":-3,\"personIds\":[");
        for (int i = 0; i < personIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(personIds.get(i));
        }
        sb.append("]}]");
        return sb.toString();
    }

    public String suggestReportNumber(long unitId, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return IncidentNumberSupport.suggestForDate(
                date,
                attendanceReportRepository.findReportNumbersForYear(
                        unitId, yearPrefix(date.getYear()), includeTestReports()));
    }

    @Transactional
    public AttendanceReport createFromEinsatzForm(
            long unitId, EinsatzberichtForm form, List<CrewAssignment> crewAssignments, AppUserDetails actor) {
        validateEinsatzForm(form);
        AttendanceReport report = newDraft(unitId);
        AnwesenheitslisteEinsatzFormBridge.applyEinsatzForm(report, form);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setReportNumber(resolveReportNumberForCreate(unitId, report.getEventDate(), form.getIncidentNumber()));
        applyCreator(report, actor);
        AttendanceReport saved = attendanceReportRepository.save(report);
        saveCrewAsPersonnel(saved, crewAssignments, unitId);
        return saved;
    }

    @Transactional
    public AttendanceReport updateFromEinsatzForm(
            long unitId,
            long reportId,
            EinsatzberichtForm form,
            List<CrewAssignment> crewAssignments,
            AppUserDetails actor,
            boolean canApprove) {
        validateEinsatzForm(form);
        AttendanceReport report = requireReport(unitId, reportId);
        ensureWritableInTestMode(report);
        if (!AnwesenheitslisteAccess.canEdit(report, actor, canApprove)) {
            throw new IllegalArgumentException("Diese Anwesenheitsliste kann nicht bearbeitet werden.");
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF
                && (actor == null || !actor.getRole().isAdminLevel())) {
            throw new IllegalArgumentException(
                    "Freigegebene oder archivierte Listen können nur von Administratoren geändert werden.");
        }
        AnwesenheitslisteEinsatzFormBridge.applyEinsatzForm(report, form);
        AttendanceReport saved = attendanceReportRepository.save(report);
        saveCrewAsPersonnel(saved, crewAssignments, unitId);
        return saved;
    }

    @Transactional
    public AttendanceReport create(long unitId, AnwesenheitslisteFormData form, AppUserDetails actor) {
        validateRequired(form);
        AttendanceReport report = newDraft(unitId);
        applyForm(report, form);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setReportNumber(resolveReportNumberForCreate(unitId, form.eventDate(), form.reportNumber()));
        applyCreator(report, actor);
        AttendanceReport saved = attendanceReportRepository.save(report);
        savePersonnel(saved, form.personnel(), unitId);
        return saved;
    }

    @Transactional
    public AttendanceReport update(
            long unitId, long reportId, AnwesenheitslisteFormData form, AppUserDetails actor, boolean canApprove) {
        validateRequired(form);
        AttendanceReport report = requireReport(unitId, reportId);
        ensureWritableInTestMode(report);
        if (!AnwesenheitslisteAccess.canEdit(report, actor, canApprove)) {
            throw new IllegalArgumentException("Diese Anwesenheitsliste kann nicht bearbeitet werden.");
        }
        if (report.getStatus() != IncidentReportStatus.ENTWURF
                && (actor == null || !actor.getRole().isAdminLevel())) {
            throw new IllegalArgumentException(
                    "Freigegebene oder archivierte Listen können nur von Administratoren geändert werden.");
        }
        applyForm(report, form);
        if (form.reportNumber() != null && !form.reportNumber().isBlank()) {
            report.setReportNumber(form.reportNumber().trim());
        }
        AttendanceReport saved = attendanceReportRepository.save(report);
        attendanceReportPersonnelRepository.deleteByReportId(saved.getId());
        savePersonnel(saved, form.personnel(), unitId);
        return saved;
    }

    @Transactional
    public void delete(long unitId, long reportId, AppUserDetails actor, boolean canApprove) {
        AttendanceReport report = requireReport(unitId, reportId);
        ensureWritableInTestMode(report);
        if (!AnwesenheitslisteAccess.canDelete(report, actor, canApprove)) {
            throw new IllegalArgumentException("Diese Anwesenheitsliste kann nicht gelöscht werden.");
        }
        attendanceReportPersonnelRepository.deleteByReportId(report.getId());
        attendanceReportRepository.delete(report);
    }

    @Transactional
    public void transitionStatus(
            long unitId, long reportId, IncidentReportStatus newStatus, AppUserDetails actor, boolean canApprove) {
        if (actor == null) {
            throw new IllegalArgumentException("Keine Berechtigung für diese Aktion.");
        }
        if (!canApprove && !actor.getRole().isAdminLevel()) {
            throw new IllegalArgumentException("Keine Berechtigung zum Ändern des Status.");
        }
        AttendanceReport report = requireReport(unitId, reportId);
        ensureWritableInTestMode(report);
        validateStatusTransition(report.getStatus(), newStatus);
        report.setStatus(newStatus);
        if (newStatus == IncidentReportStatus.FREIGEGEBEN) {
            userRepository.findById(actor.getUserId()).ifPresent(report::setReleasedByUser);
            report.setReleasedAt(Instant.now());
        }
        attendanceReportRepository.save(report);
    }

    @Transactional
    public boolean createDraftFromTerminIfMissing(long unitId, UnitTermin termin) {
        if (termin == null || termin.getId() == null) {
            return false;
        }
        if (termin.getCategory() != TermineCategory.DIENSTPLAN && termin.getCategory() != TermineCategory.SONSTIGES) {
            return false;
        }
        if (attendanceReportRepository
                .findByUnitIdAndUnitTerminId(unitId, termin.getId(), includeTestReports())
                .isPresent()) {
            return false;
        }
        AttendanceReport report = newDraft(unitId);
        applyTerminFields(report, termin);
        report.setStatus(IncidentReportStatus.ENTWURF);
        report.setCreatedByName("Terminplan");
        report.setUnitTermin(termin);
        report.setReportNumber(suggestReportNumber(unitId, report.getEventDate()));
        attendanceReportRepository.save(report);
        return true;
    }

    @Transactional
    public void refreshDraftFromTermin(long unitId, UnitTermin termin) {
        if (termin == null || termin.getId() == null) {
            return;
        }
        attendanceReportRepository
                .findByUnitIdAndUnitTerminId(unitId, termin.getId(), includeTestReports())
                .ifPresent(report -> {
                    if (report.getStatus() != IncidentReportStatus.ENTWURF) {
                        return;
                    }
                    applyTerminFields(report, termin);
                    attendanceReportRepository.save(report);
                });
    }

    @Transactional
    public void deleteDraftForTermin(long unitId, long terminId) {
        attendanceReportRepository
                .findByUnitIdAndUnitTerminId(unitId, terminId, includeTestReports())
                .ifPresent(report -> {
                    if (report.getStatus() != IncidentReportStatus.ENTWURF) {
                        return;
                    }
                    attendanceReportPersonnelRepository.deleteByReportId(report.getId());
                    attendanceReportRepository.delete(report);
                });
    }

    public List<AnwesenheitslistePersonnelRow> parsePersonnelJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<PersonnelPayload> payloads = objectMapper.readValue(json, PERSONNEL_LIST);
            List<AnwesenheitslistePersonnelRow> rows = new ArrayList<>();
            for (PersonnelPayload payload : payloads) {
                if (payload == null || payload.displayName() == null || payload.displayName().isBlank()) {
                    continue;
                }
                AttendancePersonStatus status = AttendancePersonStatus.PRESENT;
                if (payload.status() != null && !payload.status().isBlank()) {
                    try {
                        status = AttendancePersonStatus.valueOf(payload.status().trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        status = AttendancePersonStatus.PRESENT;
                    }
                }
                rows.add(new AnwesenheitslistePersonnelRow(payload.personId(), payload.displayName().trim(), status));
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Entfernt alte Termin-Vorauswahl aus der DB (früher fälschlich als „anwesend“ gespeichert).
     * Betrifft nur automatisch angelegte Entwürfe, bei denen noch alle Zielgruppen-Mitglieder eingetragen sind.
     */
    private void clearLegacyTerminPersonnelPreload(AttendanceReport report, long unitId) {
        if (report.getUnitTermin() == null || report.getStatus() != IncidentReportStatus.ENTWURF) {
            return;
        }
        if (report.getCreatedByName() == null || !"Terminplan".equals(report.getCreatedByName().trim())) {
            return;
        }
        List<Long> personnelIds = listPersonnel(report.getId()).stream()
                .map(AttendanceReportPersonnel::getPerson)
                .filter(Objects::nonNull)
                .map(Person::getId)
                .toList();
        if (personnelIds.isEmpty()) {
            return;
        }
        Set<Long> audienceIds = resolveAudiencePersons(unitId, report.getUnitTermin()).stream()
                .map(Person::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (new LinkedHashSet<>(personnelIds).equals(audienceIds)) {
            attendanceReportPersonnelRepository.deleteByReportId(report.getId());
        }
    }

    private Set<Person> resolveAudiencePersons(long unitId, UnitTermin termin) {
        if (termin.isAudienceAll()) {
            return new LinkedHashSet<>(personRepository.findActiveByUnitId(unitId, includeTestReports()));
        }
        LinkedHashSet<Person> persons = new LinkedHashSet<>();
        persons.addAll(termin.getAssignedPersons());
        for (PersonGroup group : termin.getAssignedGroups()) {
            group.getMembers().size();
            persons.addAll(group.getMembers());
        }
        return persons;
    }

    private void touchTerminAudience(UnitTermin termin) {
        if (!termin.isAudienceAll()) {
            termin.getAssignedPersons().size();
            termin.getAssignedGroups().forEach(g -> g.getMembers().size());
        }
    }

    private void applyTerminFields(AttendanceReport report, UnitTermin termin) {
        report.setEventDate(termin.getStartAt().toLocalDate());
        report.setStartTime(termin.getStartAt().toLocalTime());
        report.setEndTime(termin.getEndAt() != null ? termin.getEndAt().toLocalTime() : null);
        report.setTitle(termin.getTitle());
        report.setTerminCategory(termin.getCategory());
        String location = termin.getLocation() != null ? termin.getLocation().trim() : "";
        if (location.isBlank()) {
            UnitAddressSupport.UnitAddress unitAddress = UnitAddressSupport.fromUnit(report.getUnit());
            String unitCity = unitAddress.location();
            report.setLocation(unitCity != null && !unitCity.isBlank() ? unitCity : "—");
        } else {
            report.setLocation(location);
        }
        termin.getInstructorPersons().size();
        String instructors = formatInstructorNames(termin);
        if (instructors != null && !instructors.isBlank()) {
            report.setInstructorResponsible(instructors);
        }
    }

    private static String formatInstructorNames(UnitTermin termin) {
        List<String> names = termin.getInstructorPersons().stream()
                .sorted(Comparator.comparing(Person::anwesenheitDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(Person::anwesenheitDisplayName)
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        return String.join(", ", names);
    }

    private void validateEinsatzForm(EinsatzberichtForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Bitte alle Pflichtfelder ausfüllen.");
        }
        if (form.getIncidentDate() == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (form.getStichwort() == null || form.getStichwort().isBlank()) {
            throw new IllegalArgumentException("Bitte ein Thema angeben.");
        }
        if (form.getLocation() == null || form.getLocation().isBlank()) {
            throw new IllegalArgumentException("Bitte einen Ort angeben.");
        }
    }

    private void saveCrewAsPersonnel(AttendanceReport report, List<CrewAssignment> crewAssignments, long unitId) {
        attendanceReportPersonnelRepository.deleteByReportId(report.getId());
        if (crewAssignments == null || crewAssignments.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> personIds = new LinkedHashSet<>();
        for (CrewAssignment assignment : crewAssignments) {
            if (assignment.vehicleId() != IncidentCrewSupport.BETEILIGT_VEHICLE_ID
                    || assignment.personIds() == null) {
                continue;
            }
            assignment.personIds().stream().filter(Objects::nonNull).forEach(personIds::add);
        }
        int order = 0;
        for (Long personId : personIds) {
            var personOpt = personRepository.findActiveById(personId, includeTestReports());
            if (personOpt.isEmpty()) {
                continue;
            }
            Person person = personOpt.get();
            AttendanceReportPersonnel row = new AttendanceReportPersonnel();
            row.setAttendanceReport(report);
            row.setPerson(person);
            row.setDisplayName(person.anwesenheitDisplayName());
            row.setAttendanceStatus(AttendancePersonStatus.PRESENT);
            row.setSortOrder(order++);
            attendanceReportPersonnelRepository.save(row);
        }
    }

    private void savePersonnel(AttendanceReport report, List<AnwesenheitslistePersonnelRow> rows, long unitId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int order = 0;
        for (AnwesenheitslistePersonnelRow row : rows) {
            AttendanceReportPersonnel entity = new AttendanceReportPersonnel();
            entity.setAttendanceReport(report);
            entity.setDisplayName(row.displayName());
            entity.setAttendanceStatus(row.status() != null ? row.status() : AttendancePersonStatus.PRESENT);
            entity.setSortOrder(order++);
            if (row.personId() != null && row.personId() > 0) {
                personRepository
                        .findActiveById(row.personId(), includeTestReports())
                        .ifPresent(entity::setPerson);
            }
            attendanceReportPersonnelRepository.save(entity);
        }
    }

    private void applyForm(AttendanceReport report, AnwesenheitslisteFormData form) {
        report.setEventDate(form.eventDate());
        report.setStartTime(form.startTime());
        report.setEndTime(form.endTime());
        report.setTitle(form.title().trim());
        report.setLocation(form.location() != null ? form.location().trim() : "");
        report.setNotes(form.notes());
    }

    private void validateRequired(AnwesenheitslisteFormData form) {
        if (form == null) {
            throw new IllegalArgumentException("Bitte alle Pflichtfelder ausfüllen.");
        }
        if (form.eventDate() == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (form.title() == null || form.title().isBlank()) {
            throw new IllegalArgumentException("Bitte eine Bezeichnung angeben.");
        }
    }

    private AttendanceReport newDraft(long unitId) {
        AttendanceReport report = new AttendanceReport();
        report.setUnit(requireUnit(unitId));
        report.setEventDate(LocalDate.now());
        report.setLocation("");
        report.setTestData(testModeService.isEnabled());
        return report;
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private void applyCreator(AttendanceReport report, AppUserDetails actor) {
        if (actor == null) {
            return;
        }
        User user = userRepository.findById(actor.getUserId()).orElse(null);
        report.setCreatedByUser(user);
        report.setCreatedByName(actor.getDisplayName());
    }

    private String resolveReportNumberForCreate(long unitId, LocalDate date, String requestedNumber) {
        if (requestedNumber != null && !requestedNumber.isBlank()) {
            return requestedNumber.trim();
        }
        return suggestReportNumber(unitId, date);
    }

    private static void validateStatusTransition(IncidentReportStatus from, IncidentReportStatus to) {
        boolean valid = (from == IncidentReportStatus.ENTWURF && to == IncidentReportStatus.FREIGEGEBEN)
                || (from == IncidentReportStatus.FREIGEGEBEN && to == IncidentReportStatus.ARCHIVIERT);
        if (!valid) {
            throw new IllegalArgumentException(
                    "Ungültiger Status-Übergang: " + from.label() + " → " + to.label());
        }
    }

    private void ensureWritableInTestMode(AttendanceReport report) {
        if (testModeService.isEnabled() && !report.isTestData()) {
            throw new IllegalArgumentException("Im Testmodus können nur Test-Anwesenheitslisten bearbeitet werden.");
        }
    }

    private boolean includeTestReports() {
        return testModeService.isEnabled();
    }

    private static String yearPrefix(int year) {
        return year + "-";
    }

    private AnwesenheitslisteListItemView toListItem(AttendanceReport report) {
        TermineCategory category = report.getTerminCategory();
        String categoryKey = category != null ? category.key() : "";
        String categoryLabel = category != null ? category.displayLabel() : "Manuell";
        Long createdByUserId =
                report.getCreatedByUser() != null ? report.getCreatedByUser().getId() : null;
        return new AnwesenheitslisteListItemView(
                report.getId(),
                report.getReportNumber(),
                report.getEventDate(),
                report.getTitle(),
                categoryKey,
                categoryLabel,
                report.getLocation(),
                report.getStatus().cssModifier(),
                report.getStatus().label(),
                report.getUnitTermin() != null,
                createdByUserId);
    }

    private record PersonnelPayload(Long personId, String displayName, String status) {}
}
