package de.feuerwehr.manager.personal;

import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
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

    @Transactional
    public PersonAttendance createAttendance(
            long personId, LocalDate serviceDate, AttendanceStatus status, String notes, long actorUserId) {
        Person person = requireWritablePerson(personId);
        if (serviceDate == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (status == null) {
            status = AttendanceStatus.PRESENT;
        }
        PersonAttendance row = new PersonAttendance();
        row.setPerson(person);
        row.setServiceDate(serviceDate);
        row.setStatus(status);
        row.setNotes(blankToNull(notes));
        userRepository.findById(actorUserId).ifPresent(row::setCreatedBy);
        return attendanceRepository.save(row);
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
    public AttendanceStats attendanceStats(long personId) {
        List<PersonAttendance> rows = listAttendance(personId);
        int total = rows.size();
        int present = (int) rows.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        int absent = (int) rows.stream().filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
        int excused = (int) rows.stream().filter(r -> r.getStatus() == AttendanceStatus.EXCUSED).count();
        int pct = total > 0 ? Math.round((present * 100f) / total) : 0;
        return new AttendanceStats(total, present, absent, excused, pct);
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceCsv(long personId) {
        Person person = requireWritablePerson(personId);
        List<PersonAttendance> rows = listAttendance(personId);
        StringBuilder sb = new StringBuilder();
        sb.append("Datum;Status;Notiz\n");
        for (PersonAttendance row : rows) {
            sb.append(row.getServiceDate()).append(';');
            sb.append(row.getStatus().label()).append(';');
            sb.append(csvEscape(row.getNotes())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void updateFwHubStammdaten(
            long personId,
            LocalDate birthdate,
            String personnelNumber,
            LocalDate entryDate,
            LocalDate exitDate,
            String notes) {
        Person person = requireWritablePerson(personId);
        person.setBirthdate(birthdate);
        person.setPersonnelNumber(blankToNull(personnelNumber));
        person.setEntryDate(entryDate);
        person.setExitDate(exitDate);
        person.setNotes(blankToNull(notes));
        if (exitDate != null) {
            person.setStatus(PersonStatus.INACTIVE);
        }
        personRepository.save(person);
    }

    @Transactional
    public void updateContactData(
            long personId, String phone, String emailPrivate, String address, long actorUserId, String actorName) {
        Person person = requireWritablePerson(personId);
        person.setPhone(blankToNull(phone));
        person.setEmailPrivate(blankToNull(emailPrivate));
        person.setAddress(blankToNull(address));
        userRepository.findById(actorUserId).ifPresent(person::setProfileUpdatedBy);
        person.setProfileUpdatedByName(actorName);
        personRepository.save(person);
    }

    @Transactional(readOnly = true)
    public List<PersonEmergencyContact> listEmergencyContacts(long personId) {
        Person person = requireWritablePerson(personId);
        return emergencyContactRepository.findByPersonIdOrderBySortOrderAscNameAsc(person.getId());
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
}
