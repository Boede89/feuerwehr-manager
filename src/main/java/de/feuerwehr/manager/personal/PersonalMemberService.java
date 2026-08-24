package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.berichte.AnwesenheitslisteService;
import de.feuerwehr.manager.berichte.AttendanceReport;
import de.feuerwehr.manager.berichte.AttendanceReportRepository;
import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportPersonnel;
import de.feuerwehr.manager.berichte.IncidentReportPersonnelRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.berichte.IncidentReportStatus;
import de.feuerwehr.manager.security.AccessControlService;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.termine.TermineCategory;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.util.YearFilterSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonalMemberService {

    private final PersonRepository personRepository;
    private final PersonQualificationRepository qualificationRepository;
    private final PersonEquipmentRepository equipmentRepository;
    private final PersonHonorRepository honorRepository;
    private final PersonAttendanceRepository attendanceRepository;
    private final PersonEmergencyContactRepository emergencyContactRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;
    private final GlobalSettingsService globalSettingsService;
    private final AccessControlService accessControlService;
    private final TestModeService testModeService;
    private final AttendanceReportRepository attendanceReportRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentReportPersonnelRepository incidentReportPersonnelRepository;
    private final AnwesenheitslisteService anwesenheitslisteService;

    private static final Set<IncidentReportStatus> RELEASED_STATUSES =
            EnumSet.of(IncidentReportStatus.FREIGEGEBEN, IncidentReportStatus.ARCHIVIERT);

    @Transactional(readOnly = true)
    public int qualificationWarnDays() {
        return globalSettingsService.get().getQualificationWarnDays();
    }

    @Transactional(readOnly = true)
    public List<PersonQualification> listQualifications(long personId) {
        requireWritablePerson(personId);
        return qualificationRepository.findByPersonIdOrderByNameAsc(personId);
    }

    @Transactional
    public PersonQualification createQualification(
            long personId,
            String name,
            LocalDate acquiredAt,
            LocalDate expiresAt,
            String notes,
            boolean healthData) {
        Person person = requireWritablePerson(personId);
        validateName(name, "Qualifikation");
        PersonQualification q = new PersonQualification();
        q.setPerson(person);
        q.setName(name.trim());
        q.setAcquiredAt(acquiredAt);
        q.setExpiresAt(expiresAt);
        q.setNotes(blankToNull(notes));
        q.setHealthData(healthData);
        return qualificationRepository.save(q);
    }

    @Transactional
    public void updateQualification(
            long personId,
            long qualificationId,
            String name,
            LocalDate acquiredAt,
            LocalDate expiresAt,
            String notes,
            boolean healthData) {
        requireWritablePerson(personId);
        validateName(name, "Qualifikation");
        PersonQualification q = qualificationRepository
                .findById(qualificationId)
                .filter(row -> row.getPerson().getId().equals(personId))
                .orElseThrow(() -> new IllegalArgumentException("Qualifikation nicht gefunden."));
        q.setName(name.trim());
        q.setAcquiredAt(acquiredAt);
        q.setExpiresAt(expiresAt);
        q.setNotes(blankToNull(notes));
        q.setHealthData(healthData);
        qualificationRepository.save(q);
    }

    @Transactional
    public void deleteQualification(long personId, long qualificationId) {
        requireWritablePerson(personId);
        if (!qualificationRepository.existsByIdAndPersonId(qualificationId, personId)) {
            throw new IllegalArgumentException("Qualifikation nicht gefunden.");
        }
        qualificationRepository.deleteById(qualificationId);
    }

    @Transactional(readOnly = true)
    public List<PersonEquipment> listEquipment(long personId) {
        requireWritablePerson(personId);
        return equipmentRepository.findByPersonIdOrderByCreatedAtDesc(personId);
    }

    @Transactional
    public PersonEquipment createEquipment(
            long personId,
            EquipmentType type,
            String identifier,
            LocalDate issuedAt,
            LocalDate expiresAt,
            String notes) {
        Person person = requireWritablePerson(personId);
        if (type == null) {
            throw new IllegalArgumentException("Bitte einen Typ wählen.");
        }
        PersonEquipment e = new PersonEquipment();
        e.setPerson(person);
        e.setType(type);
        e.setIdentifier(blankToNull(identifier));
        e.setIssuedAt(issuedAt);
        e.setExpiresAt(expiresAt);
        e.setNotes(blankToNull(notes));
        return equipmentRepository.save(e);
    }

    @Transactional
    public void updateEquipment(
            long personId,
            long equipmentId,
            EquipmentType type,
            String identifier,
            LocalDate issuedAt,
            LocalDate expiresAt,
            String notes) {
        requireWritablePerson(personId);
        if (type == null) {
            throw new IllegalArgumentException("Bitte einen Typ wählen.");
        }
        PersonEquipment e = equipmentRepository
                .findById(equipmentId)
                .filter(row -> row.getPerson().getId().equals(personId))
                .orElseThrow(() -> new IllegalArgumentException("Ausrüstung nicht gefunden."));
        e.setType(type);
        e.setIdentifier(blankToNull(identifier));
        e.setIssuedAt(issuedAt);
        e.setExpiresAt(expiresAt);
        e.setNotes(blankToNull(notes));
        equipmentRepository.save(e);
    }

    @Transactional
    public void deleteEquipment(long personId, long equipmentId) {
        requireWritablePerson(personId);
        if (!equipmentRepository.existsByIdAndPersonId(equipmentId, personId)) {
            throw new IllegalArgumentException("Ausrüstung nicht gefunden.");
        }
        equipmentRepository.deleteById(equipmentId);
    }

    @Transactional(readOnly = true)
    public List<PersonHonor> listHonors(long personId) {
        requireWritablePerson(personId);
        return honorRepository.findByPersonIdOrderByAwardedAtDescNameAsc(personId);
    }

    @Transactional
    public PersonHonor createHonor(
            long personId, String name, LocalDate awardedAt, String status, String notes) {
        Person person = requireWritablePerson(personId);
        validateName(name, "Ehrung");
        PersonHonor h = new PersonHonor();
        h.setPerson(person);
        h.setName(name.trim());
        h.setAwardedAt(awardedAt);
        h.setStatus(normalizeHonorStatus(status));
        h.setNotes(blankToNull(notes));
        return honorRepository.save(h);
    }

    @Transactional
    public void updateHonor(
            long personId, long honorId, String name, LocalDate awardedAt, String status, String notes) {
        requireWritablePerson(personId);
        validateName(name, "Ehrung");
        PersonHonor h = honorRepository
                .findById(honorId)
                .filter(row -> row.getPerson().getId().equals(personId))
                .orElseThrow(() -> new IllegalArgumentException("Ehrung nicht gefunden."));
        h.setName(name.trim());
        h.setAwardedAt(awardedAt);
        h.setStatus(normalizeHonorStatus(status));
        h.setNotes(blankToNull(notes));
        honorRepository.save(h);
    }

    @Transactional
    public void deleteHonor(long personId, long honorId) {
        requireWritablePerson(personId);
        if (!honorRepository.existsByIdAndPersonId(honorId, personId)) {
            throw new IllegalArgumentException("Ehrung nicht gefunden.");
        }
        honorRepository.deleteById(honorId);
    }

    @Transactional(readOnly = true)
    public List<PersonAttendance> listAttendance(long personId) {
        requireWritablePerson(personId);
        return attendanceRepository.findByPersonIdOrderByServiceDateDesc(personId);
    }

    @Transactional(readOnly = true)
    public AttendancePage loadAttendancePage(long personId) {
        Person person = requireWritablePerson(personId);
        long unitId = person.getUnit().getId();
        boolean includeTest = testModeService.isEnabled();
        LocalDate from = person.getEntryDate() != null ? person.getEntryDate() : LocalDate.of(1990, 1, 1);
        LocalDate to = LocalDate.now();
        if (person.getExitDate() != null && person.getExitDate().isBefore(to)) {
            to = person.getExitDate();
        }

        List<AttendanceEventView> events = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        int possibleUebungen = 0;
        List<AttendanceReport> lists =
                attendanceReportRepository.findByUnitIdAndDateRange(unitId, from, to, includeTest).stream()
                        .filter(report -> RELEASED_STATUSES.contains(report.getStatus()))
                        .filter(report -> report.getTerminCategory() == null
                                || report.getTerminCategory().supportsAttendanceReports())
                        .toList();
        for (AttendanceReport report : lists) {
            if (isUebungsdienst(report)) {
                possibleUebungen++;
            }
            Set<Long> presentIds = anwesenheitslisteService.presentAndPaPersonIds(unitId, report.getId()).presentIds();
            if (!presentIds.contains(personId)) {
                continue;
            }
            AttendanceServiceType type = isUebungsdienst(report)
                    ? AttendanceServiceType.UEBUNGSDIENST
                    : AttendanceServiceType.SONSTIGES;
            String label = blankToDash(
                    report.getTitle(),
                    isUebungsdienst(report) ? "Übungsdienst" : "Dienst");
            AttendanceEventView view = new AttendanceEventView(
                    null,
                    label,
                    type,
                    report.getEventDate(),
                    false,
                    "/berichte/anwesenheitslisten/" + report.getId() + "?unit=" + unitId);
            if (seen.add(eventKey(view))) {
                events.add(view);
            }
        }

        Set<Long> seenIncidentIds = new HashSet<>();
        for (IncidentReportPersonnel row : incidentReportPersonnelRepository.findByPersonAndUnit(
                personId, unitId, RELEASED_STATUSES, includeTest)) {
            IncidentReport report = row.getIncidentReport();
            if (report == null || report.getId() == null || !seenIncidentIds.add(report.getId())) {
                continue;
            }
            LocalDate date = report.getIncidentDate();
            if (date == null || !YearFilterSupport.isWithinMembership(date, person.getEntryDate(), person.getExitDate())) {
                continue;
            }
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            String label = blankToDash(report.getStichwort(), "Einsatz");
            AttendanceEventView view = new AttendanceEventView(
                    null,
                    label,
                    AttendanceServiceType.EINSATZ,
                    date,
                    false,
                    "/berichte/einsatzberichte/" + report.getId() + "?unit=" + unitId);
            if (seen.add(eventKey(view))) {
                events.add(view);
            }
        }

        for (PersonAttendance row : attendanceRepository.findByPersonIdOrderByServiceDateDesc(personId)) {
            AttendanceEventView view = new AttendanceEventView(
                    row.getId(),
                    blankToDash(row.getServiceLabel(), "—"),
                    row.getServiceType(),
                    row.getServiceDate(),
                    true,
                    null);
            if (seen.add(eventKey(view))) {
                events.add(view);
            }
        }

        events.sort(Comparator.comparing(
                        AttendanceEventView::serviceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(AttendanceEventView::serviceLabel, String.CASE_INSENSITIVE_ORDER));

        int uebungsdienste = (int) events.stream()
                .filter(row -> row.serviceType() == AttendanceServiceType.UEBUNGSDIENST)
                .count();
        int einsaetze = (int) events.stream()
                .filter(row -> row.serviceType() == AttendanceServiceType.EINSATZ)
                .count();
        int possibleEinsaetze = (int) incidentReportRepository
                .findByUnitIdAndYear(unitId, from, to.plusDays(1), includeTest)
                .stream()
                .filter(report -> RELEASED_STATUSES.contains(report.getStatus()))
                .count();
        int possible = possibleUebungen + possibleEinsaetze;
        int attended = uebungsdienste + einsaetze;
        String quoteLabel = possible > 0 ? Math.round((attended * 100f) / possible) + " %" : "–";
        return new AttendancePage(
                new AttendanceDisplayStats(events.size(), uebungsdienste, einsaetze, quoteLabel),
                List.copyOf(events));
    }

    @Transactional(readOnly = true)
    public List<AttendanceEventView> listAttendanceEvents(long personId) {
        return loadAttendancePage(personId).events();
    }

    /**
     * Anwesenheits-Kennzahlen aus Anwesenheitslisten, Einsatzberichten und manuellen Einträgen.
     */
    @Transactional(readOnly = true)
    public AttendanceDisplayStats displayAttendanceStats(long personId) {
        return loadAttendancePage(personId).stats();
    }

    @Transactional
    public PersonAttendance createAttendance(
            long personId,
            LocalDate serviceDate,
            AttendanceServiceType serviceType,
            String serviceLabel,
            AttendanceStatus status,
            String notes,
            long actorUserId) {
        Person person = requireWritablePerson(personId);
        if (serviceDate == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (serviceType == null) {
            throw new IllegalArgumentException("Bitte einen Typ wählen.");
        }
        if (status == null) {
            status = AttendanceStatus.PRESENT;
        }
        PersonAttendance row = new PersonAttendance();
        row.setPerson(person);
        row.setServiceDate(serviceDate);
        row.setServiceType(serviceType);
        row.setServiceLabel(blankToNull(serviceLabel));
        row.setStatus(status);
        row.setNotes(blankToNull(notes));
        userRepository.findById(actorUserId).ifPresent(row::setCreatedBy);
        return attendanceRepository.save(row);
    }

    @Transactional
    public void updateAttendance(
            long personId,
            long attendanceId,
            LocalDate serviceDate,
            AttendanceServiceType serviceType,
            String serviceLabel,
            AttendanceStatus status,
            String notes) {
        requireWritablePerson(personId);
        PersonAttendance row = attendanceRepository
                .findById(attendanceId)
                .filter(entry -> entry.getPerson().getId().equals(personId))
                .orElseThrow(() -> new IllegalArgumentException("Eintrag nicht gefunden."));
        if (serviceDate == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (serviceType == null) {
            throw new IllegalArgumentException("Bitte einen Typ wählen.");
        }
        if (status == null) {
            status = AttendanceStatus.PRESENT;
        }
        row.setServiceDate(serviceDate);
        row.setServiceType(serviceType);
        row.setServiceLabel(blankToNull(serviceLabel));
        row.setStatus(status);
        row.setNotes(blankToNull(notes));
        attendanceRepository.save(row);
    }

    @Transactional
    public void deleteAttendance(long personId, long attendanceId) {
        requireWritablePerson(personId);
        if (!attendanceRepository.existsByIdAndPersonId(attendanceId, personId)) {
            throw new IllegalArgumentException("Eintrag nicht gefunden.");
        }
        attendanceRepository.deleteById(attendanceId);
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceCsv(long personId) {
        List<AttendanceEventView> rows = listAttendanceEvents(personId);
        StringBuilder sb = new StringBuilder();
        sb.append("Bezeichnung;Typ;Datum;Quelle\n");
        for (AttendanceEventView row : rows) {
            sb.append(csvEscape(row.serviceLabel())).append(';');
            sb.append(row.serviceType().label()).append(';');
            sb.append(row.serviceDate()).append(';');
            sb.append(row.editable() ? "Manuell" : "Bericht").append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void updateFwHubStammdaten(
            long personId,
            String firstName,
            String lastName,
            LocalDate birthdate,
            String personnelNumber,
            LocalDate entryDate,
            LocalDate exitDate,
            String notes) {
        personalService.updatePersonNames(personId, firstName, lastName);
        Person person = requireWritablePerson(personId);
        person.setBirthdate(birthdate);
        person.setPersonnelNumber(blankToNull(personnelNumber));
        person.setEntryDate(entryDate);
        person.setExitDate(exitDate);
        person.setNotes(blankToNull(notes));
        if (exitDate != null) {
            person.setStatus(PersonStatus.INACTIVE);
        } else if (person.getStatus() == PersonStatus.INACTIVE) {
            person.setStatus(PersonStatus.ACTIVE);
        }
        personRepository.save(person);
    }

    @Transactional(readOnly = true)
    public String resolvePersonEmail(Person person) {
        if (person.getUser() != null
                && person.getUser().getLoginEmail() != null
                && !person.getUser().getLoginEmail().isBlank()) {
            return person.getUser().getLoginEmail();
        }
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            return person.getEmail();
        }
        return person.getEmailPrivate();
    }

    @Transactional
    public void updateContactData(
            long personId, String phone, String email, String address, long actorUserId, String actorName) {
        Person person = requireWritablePerson(personId);
        String normalized = normalizeEmail(email);
        person.setPhone(blankToNull(phone));
        person.setEmail(normalized);
        person.setEmailPrivate(null);
        person.setAddress(blankToNull(address));
        userRepository.findById(actorUserId).ifPresent(person::setProfileUpdatedBy);
        person.setProfileUpdatedByName(actorName);
        if (person.getUser() != null) {
            long linkedUserId = person.getUser().getId();
            if (normalized != null
                    && userRepository
                            .findByLoginEmailIgnoreCaseExcludingId(normalized, linkedUserId)
                            .isPresent()) {
                throw new IllegalArgumentException("Diese E-Mail-Adresse wird bereits verwendet.");
            }
            User user = person.getUser();
            user.setLoginEmail(normalized);
            userRepository.save(user);
        }
        personRepository.save(person);
    }

    @Transactional(readOnly = true)
    public List<PersonEmergencyContact> listEmergencyContacts(long personId) {
        Person person = requireWritablePerson(personId);
        return emergencyContactRepository.findByPersonIdOrderBySortOrderAscNameAsc(person.getId());
    }

    @Transactional
    public PersonalService.StammdatenUpdateResult updateLoginAccess(
            long personId,
            boolean allowLogin,
            String passwordDelivery,
            String manualPassword,
            AppUserDetails actor,
            jakarta.servlet.http.HttpServletRequest request) {
        return personalService.updateLoginAccess(
                personId, allowLogin, passwordDelivery, manualPassword, actor, request);
    }

    @Transactional
    public void deletePerson(long personId, AppUserDetails actor, jakarta.servlet.http.HttpServletRequest request) {
        Person person = personalService.requirePerson(personId);
        accessControlService.requireCanDeletePerson(actor, person);
        personalService.anonymizePerson(personId, actor, request);
    }

    @Transactional
    public void addCourseCompletion(long personId, long courseId, Integer completionYear) {
        personalService.addCourseCompletion(personId, courseId, completionYear);
    }

    @Transactional
    public void updateCourseCompletion(long personId, long completionId, long courseId, Integer completionYear) {
        personalService.updateCourseCompletion(personId, completionId, courseId, completionYear);
    }

    @Transactional
    public void deleteCourseCompletion(long personId, long completionId) {
        personalService.deleteCourseCompletion(personId, completionId);
    }

    private Person requireWritablePerson(long personId) {
        return personalService.requirePerson(personId);
    }

    private static void validateName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Bezeichnung für " + label + " eingeben.");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private static String normalizeHonorStatus(String status) {
        if (status == null || status.isBlank()) {
            return "aktiv";
        }
        return "zurueckgezogen".equalsIgnoreCase(status.trim()) ? "zurueckgezogen" : "aktiv";
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\"", "\"\"");
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    public record AttendanceStats(int total, int present, int absent, int excused, int presentPercent) {}

    /** Kennzahlen für Reiter Anwesenheit (Gesamt, Übungsdienste, Einsätze, Quote). */
    public record AttendanceDisplayStats(
            int total, int uebungsdienste, int einsaetze, String quoteLabel) {}

    public record AttendancePage(AttendanceDisplayStats stats, List<AttendanceEventView> events) {}

    public record AttendanceEventView(
            Long id,
            String serviceLabel,
            AttendanceServiceType serviceType,
            LocalDate serviceDate,
            boolean editable,
            String href) {}

    private static boolean isUebungsdienst(AttendanceReport report) {
        return report.getTerminCategory() == null || report.getTerminCategory() == TermineCategory.DIENSTPLAN;
    }

    private static String eventKey(AttendanceEventView view) {
        String date = view.serviceDate() != null ? view.serviceDate().toString() : "";
        String label = view.serviceLabel() != null ? view.serviceLabel().trim().toLowerCase() : "";
        return view.serviceType().name() + "|" + date + "|" + label;
    }

    private static String blankToDash(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
