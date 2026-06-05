package de.feuerwehr.manager.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.atemschutz.AtemschutzCarrier;
import de.feuerwehr.manager.atemschutz.AtemschutzCarrierRepository;
import de.feuerwehr.manager.atemschutz.AtemschutzCarrierStatus;
import de.feuerwehr.manager.atemschutz.AtemschutzEmailTemplate;
import de.feuerwehr.manager.atemschutz.AtemschutzEmailTemplateRepository;
import de.feuerwehr.manager.atemschutz.AtemschutzFitnessRecord;
import de.feuerwehr.manager.atemschutz.AtemschutzFitnessRecordRepository;
import de.feuerwehr.manager.atemschutz.AtemschutzFitnessType;
import de.feuerwehr.manager.atemschutz.AtemschutzService;
import de.feuerwehr.manager.atemschutz.AtemschutzSettingsService;
import de.feuerwehr.manager.atemschutz.UnitAtemschutzSettings;
import de.feuerwehr.manager.atemschutz.UnitAtemschutzSettingsRepository;
import de.feuerwehr.manager.personal.Course;
import de.feuerwehr.manager.personal.CourseRepository;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonAttendance;
import de.feuerwehr.manager.personal.PersonAttendanceRepository;
import de.feuerwehr.manager.personal.PersonCourseCompletion;
import de.feuerwehr.manager.personal.PersonCourseCompletionRepository;
import de.feuerwehr.manager.personal.PersonDiveraRic;
import de.feuerwehr.manager.personal.PersonDiveraRicRepository;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonStatus;
import de.feuerwehr.manager.personal.QualificationType;
import de.feuerwehr.manager.personal.QualificationTypeRepository;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.UserRole;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitPersonalAtemschutzTransferService {

    private final UnitRepository unitRepository;
    private final PersonRepository personRepository;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final CourseRepository courseRepository;
    private final PersonCourseCompletionRepository personCourseCompletionRepository;
    private final PersonGroupRepository personGroupRepository;
    private final PersonAttendanceRepository personAttendanceRepository;
    private final PersonDiveraRicRepository personDiveraRicRepository;
    private final AtemschutzCarrierRepository atemschutzCarrierRepository;
    private final AtemschutzFitnessRecordRepository fitnessRecordRepository;
    private final UnitAtemschutzSettingsRepository atemschutzSettingsRepository;
    private final AtemschutzEmailTemplateRepository emailTemplateRepository;
    private final AtemschutzSettingsService atemschutzSettingsService;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public UnitDataExportDocument export(long unitId) {
        Unit unit = requireUnit(unitId);
        boolean testData = testModeService.isEnabled();
        UnitDataExportDocument doc = new UnitDataExportDocument();
        doc.setExportedAt(Instant.now().toString());
        doc.setUnitName(unit.getName());
        doc.setUnitId(unitId);

        Map<Long, Long> personSourceIds = new HashMap<>();
        for (QualificationType qt : qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testData)) {
            UnitDataExportDocument.QualificationTypeRow row = new UnitDataExportDocument.QualificationTypeRow();
            row.setSourceId(sourceId(qt.getProductionSourceId(), qt.getId()));
            row.setName(qt.getName());
            row.setSortOrder(qt.getSortOrder());
            row.setActive(qt.isActive());
            doc.getQualificationTypes().add(row);
        }
        Map<Long, Long> courseSourceIds = new HashMap<>();
        for (Course course : courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testData)) {
            UnitDataExportDocument.CourseRow row = new UnitDataExportDocument.CourseRow();
            long sourceId = sourceId(course.getProductionSourceId(), course.getId());
            courseSourceIds.put(course.getId(), sourceId);
            row.setSourceId(sourceId);
            row.setName(course.getName());
            row.setSortOrder(course.getSortOrder());
            row.setActive(course.isActive());
            if (course.getQualificationType() != null) {
                row.setQualificationTypeSourceId(
                        sourceId(course.getQualificationType().getProductionSourceId(), course.getQualificationType().getId()));
            }
            doc.getCourses().add(row);
        }
        List<Person> persons = personRepository.findActiveByUnitId(unitId, testData);
        for (Person person : persons) {
            long sourceId = sourceId(person.getProductionSourceId(), person.getId());
            personSourceIds.put(person.getId(), sourceId);
            doc.getPersons().add(toPersonRow(person, sourceId));
            for (PersonCourseCompletion cc : personCourseCompletionRepository.findByPersonId(person.getId())) {
                UnitDataExportDocument.CourseCompletionRow row = new UnitDataExportDocument.CourseCompletionRow();
                row.setPersonSourceId(sourceId);
                row.setCourseSourceId(courseSourceIds.getOrDefault(
                        cc.getCourse().getId(), sourceId(cc.getCourse().getProductionSourceId(), cc.getCourse().getId())));
                row.setCompletionYear(cc.getCompletionYear());
                row.setCompletedOn(formatDate(cc.getCompletedOn()));
                doc.getPersonCourseCompletions().add(row);
            }
            for (PersonAttendance att : personAttendanceRepository.findByPersonIdOrderByServiceDateDesc(person.getId())) {
                UnitDataExportDocument.AttendanceRow row = new UnitDataExportDocument.AttendanceRow();
                row.setPersonSourceId(sourceId);
                row.setServiceDate(formatDate(att.getServiceDate()));
                row.setServiceLabel(att.getServiceLabel());
                row.setServiceType(att.getServiceType() != null ? att.getServiceType().name() : null);
                row.setStatus(att.getStatus() != null ? att.getStatus().name() : null);
                row.setNotes(att.getNotes());
                doc.getPersonAttendance().add(row);
            }
            for (PersonDiveraRic ric : personDiveraRicRepository.findByPersonIdOrderByRicCodeAsc(person.getId())) {
                UnitDataExportDocument.RicRow row = new UnitDataExportDocument.RicRow();
                row.setPersonSourceId(sourceId);
                row.setRicCode(ric.getRicCode());
                doc.getPersonDiveraRics().add(row);
            }
        }
        for (PersonGroup group : personGroupRepository.findByUnitIdWithMembers(unitId, testData)) {
            UnitDataExportDocument.GroupRow row = new UnitDataExportDocument.GroupRow();
            row.setSourceId(sourceId(null, group.getId()));
            row.setName(group.getName());
            for (Person member : group.getMembers()) {
                row.getMemberSourceIds().add(personSourceIds.getOrDefault(member.getId(), member.getId()));
            }
            doc.getPersonGroups().add(row);
        }
        for (AtemschutzCarrier carrier : atemschutzCarrierRepository.findByUnitId(unitId, testData)) {
            long personSourceId = personSourceIds.getOrDefault(carrier.getPerson().getId(), carrier.getPerson().getId());
            UnitDataExportDocument.CarrierRow row = new UnitDataExportDocument.CarrierRow();
            row.setPersonSourceId(personSourceId);
            row.setStatus(carrier.getStatus().name());
            row.setNotes(carrier.getNotes());
            doc.getAtemschutzCarriers().add(row);
            for (AtemschutzFitnessRecord record : fitnessRecordRepository.findByCarrierId(carrier.getId(), testData)) {
                UnitDataExportDocument.FitnessRecordRow fr = new UnitDataExportDocument.FitnessRecordRow();
                fr.setPersonSourceId(personSourceId);
                fr.setRecordType(record.getRecordType().name());
                fr.setValidFrom(formatDate(record.getValidFrom()));
                fr.setValidUntil(formatDate(record.getValidUntil()));
                fr.setPhysician(record.getPhysician());
                fr.setResultNotes(record.getResultNotes());
                fr.setSourceLabel(record.getSourceLabel());
                doc.getAtemschutzFitnessRecords().add(fr);
            }
        }
        atemschutzSettingsRepository.findByUnitId(unitId).ifPresent(settings -> {
            UnitDataExportDocument.AtemschutzSettingsRow row = new UnitDataExportDocument.AtemschutzSettingsRow();
            row.setWarnDays(settings.getWarnDays());
            row.setAgtCourseName(settings.getAgtCourseName());
            if (settings.getAgtCourse() != null) {
                row.setAgtCourseSourceId(sourceId(
                        settings.getAgtCourse().getProductionSourceId(), settings.getAgtCourse().getId()));
            }
            row.setNotificationUserIds(settings.getNotificationUserIds());
            row.setCcUserIds(settings.getCcUserIds());
            doc.setAtemschutzSettings(row);
        });
        for (AtemschutzEmailTemplate tpl : emailTemplateRepository.findByUnitIdOrderByTemplateKeyAsc(unitId)) {
            UnitDataExportDocument.EmailTemplateRow row = new UnitDataExportDocument.EmailTemplateRow();
            row.setTemplateKey(tpl.getTemplateKey());
            row.setTemplateName(tpl.getTemplateName());
            row.setSubject(tpl.getSubject());
            row.setBody(tpl.getBody());
            doc.getAtemschutzEmailTemplates().add(row);
        }
        return doc;
    }

    public byte[] exportJson(long unitId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export(unitId));
        } catch (Exception e) {
            throw new IllegalStateException("Export fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ImportSummary importJson(long unitId, byte[] json, boolean replaceExisting) {
        UnitDataExportDocument doc;
        try {
            doc = objectMapper.readValue(json, UnitDataExportDocument.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ungültige JSON-Datei: " + e.getMessage());
        }
        if (!UnitDataExportDocument.FORMAT.equals(doc.getFormat())) {
            throw new IllegalArgumentException("Unbekanntes Export-Format.");
        }
        if (doc.getVersion() != UnitDataExportDocument.VERSION) {
            throw new IllegalArgumentException("Nicht unterstützte Export-Version: " + doc.getVersion());
        }
        if (replaceExisting) {
            clearUnitPersonalAndAtemschutz(unitId);
        }
        return importDocument(unitId, doc);
    }

    @Transactional
    public ImportSummary importLegacySql(long unitId, String sql, Integer sourceEinheitId, boolean replaceExisting) {
        Map<String, List<Map<String, String>>> tables = LegacySqlInsertParser.parse(sql);
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("Keine INSERT-Daten in der SQL-Datei gefunden.");
        }
        if (replaceExisting) {
            clearUnitPersonalAndAtemschutz(unitId);
        }
        Unit unit = requireUnit(unitId);
        boolean testData = testModeService.isEnabled();
        ImportSummary summary = new ImportSummary();

        Map<Long, QualificationType> qualificationBySource = importLegacyQualifications(unit, tables, sourceEinheitId, testData, summary);
        Map<Long, Course> courseBySource = importLegacyCourses(unit, tables, sourceEinheitId, testData, qualificationBySource, summary);
        Map<Long, Person> personByMemberId = importLegacyMembers(unit, tables, sourceEinheitId, testData, qualificationBySource, summary);
        importLegacyMemberCourses(tables, personByMemberId, courseBySource, summary);
        importLegacyGroups(unit, tables, sourceEinheitId, testData, personByMemberId, summary);
        importLegacyAtemschutz(unit, tables, sourceEinheitId, testData, personByMemberId, summary);
        return summary;
    }

    private ImportSummary importDocument(long unitId, UnitDataExportDocument doc) {
        Unit unit = requireUnit(unitId);
        boolean testData = testModeService.isEnabled();
        ImportSummary summary = new ImportSummary();

        Map<Long, QualificationType> qualifications = new HashMap<>();
        for (UnitDataExportDocument.QualificationTypeRow row : doc.getQualificationTypes()) {
            QualificationType qt = findOrCreateQualification(unit, testData, row.getSourceId(), row.getName());
            qt.setSortOrder(row.getSortOrder() != null ? row.getSortOrder() : qt.getSortOrder());
            if (row.getActive() != null) {
                qt.setActive(row.getActive());
            }
            qualifications.put(row.getSourceId(), qualificationTypeRepository.save(qt));
            summary.qualifications++;
        }
        Map<Long, Course> courses = new HashMap<>();
        for (UnitDataExportDocument.CourseRow row : doc.getCourses()) {
            Course course = findOrCreateCourse(unit, testData, row.getSourceId(), row.getName());
            course.setSortOrder(row.getSortOrder() != null ? row.getSortOrder() : course.getSortOrder());
            if (row.getActive() != null) {
                course.setActive(row.getActive());
            }
            if (row.getQualificationTypeSourceId() != null) {
                course.setQualificationType(qualifications.get(row.getQualificationTypeSourceId()));
            }
            courses.put(row.getSourceId(), courseRepository.save(course));
            summary.courses++;
        }
        Map<Long, Person> persons = new HashMap<>();
        for (UnitDataExportDocument.PersonRow row : doc.getPersons()) {
            Person person = findOrCreatePerson(unit, testData, row.getSourceId());
            applyPersonRow(person, row);
            if (row.getQualificationTypeSourceId() != null) {
                person.setQualificationType(qualifications.get(row.getQualificationTypeSourceId()));
            }
            persons.put(row.getSourceId(), personRepository.save(person));
            summary.persons++;
        }
        for (UnitDataExportDocument.CourseCompletionRow row : doc.getPersonCourseCompletions()) {
            Person person = persons.get(row.getPersonSourceId());
            Course course = courses.get(row.getCourseSourceId());
            if (person == null || course == null) {
                continue;
            }
            if (personCourseCompletionRepository.existsByPersonIdAndCourseId(person.getId(), course.getId())) {
                continue;
            }
            PersonCourseCompletion cc = new PersonCourseCompletion();
            cc.setPerson(person);
            cc.setCourse(course);
            cc.setCompletionYear(row.getCompletionYear());
            cc.setCompletedOn(parseDate(row.getCompletedOn()));
            personCourseCompletionRepository.save(cc);
            summary.courseCompletions++;
        }
        for (UnitDataExportDocument.GroupRow row : doc.getPersonGroups()) {
            PersonGroup group = personGroupRepository
                    .findByUnitIdWithMembers(unitId, testData).stream()
                    .filter(g -> g.getName().equalsIgnoreCase(row.getName()))
                    .findFirst()
                    .orElseGet(() -> {
                        PersonGroup created = new PersonGroup();
                        created.setUnit(unit);
                        created.setName(row.getName());
                        created.setTestData(testData);
                        return created;
                    });
            group.getMembers().clear();
            for (Long memberSourceId : row.getMemberSourceIds()) {
                Person person = persons.get(memberSourceId);
                if (person != null) {
                    group.getMembers().add(person);
                }
            }
            personGroupRepository.save(group);
            summary.groups++;
        }
        for (UnitDataExportDocument.AttendanceRow row : doc.getPersonAttendance()) {
            Person person = persons.get(row.getPersonSourceId());
            if (person == null) {
                continue;
            }
            PersonAttendance att = new PersonAttendance();
            att.setPerson(person);
            att.setServiceDate(parseDateRequired(row.getServiceDate(), "service_date"));
            att.setServiceLabel(row.getServiceLabel());
            att.setServiceType(parseEnum(row.getServiceType(), de.feuerwehr.manager.personal.AttendanceServiceType.class, de.feuerwehr.manager.personal.AttendanceServiceType.UEBUNGSDIENST));
            att.setStatus(parseEnum(row.getStatus(), de.feuerwehr.manager.personal.AttendanceStatus.class, de.feuerwehr.manager.personal.AttendanceStatus.PRESENT));
            att.setNotes(row.getNotes());
            personAttendanceRepository.save(att);
            summary.attendance++;
        }
        for (UnitDataExportDocument.RicRow row : doc.getPersonDiveraRics()) {
            Person person = persons.get(row.getPersonSourceId());
            if (person == null || blank(row.getRicCode())) {
                continue;
            }
            PersonDiveraRic ric = new PersonDiveraRic();
            ric.setPerson(person);
            ric.setRicCode(row.getRicCode().trim());
            personDiveraRicRepository.save(ric);
            summary.rics++;
        }
        Map<Long, AtemschutzCarrier> carriers = new HashMap<>();
        for (UnitDataExportDocument.CarrierRow row : doc.getAtemschutzCarriers()) {
            Person person = persons.get(row.getPersonSourceId());
            if (person == null) {
                continue;
            }
            AtemschutzCarrier carrier = atemschutzCarrierRepository.findByUnitId(unitId, testData).stream()
                    .filter(c -> c.getPerson().getId().equals(person.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        AtemschutzCarrier created = new AtemschutzCarrier();
                        created.setUnit(unit);
                        created.setPerson(person);
                        created.setTestData(testData);
                        return created;
                    });
            carrier.setStatus(parseEnum(row.getStatus(), AtemschutzCarrierStatus.class, AtemschutzCarrierStatus.ACTIVE));
            carrier.setNotes(row.getNotes());
            carriers.put(row.getPersonSourceId(), atemschutzCarrierRepository.save(carrier));
            summary.carriers++;
        }
        for (UnitDataExportDocument.FitnessRecordRow row : doc.getAtemschutzFitnessRecords()) {
            AtemschutzCarrier carrier = carriers.get(row.getPersonSourceId());
            if (carrier == null) {
                Person person = persons.get(row.getPersonSourceId());
                if (person == null) {
                    continue;
                }
                carrier = new AtemschutzCarrier();
                carrier.setUnit(unit);
                carrier.setPerson(person);
                carrier.setTestData(testData);
                carrier.setStatus(AtemschutzCarrierStatus.ACTIVE);
                carrier = atemschutzCarrierRepository.save(carrier);
                carriers.put(row.getPersonSourceId(), carrier);
                summary.carriers++;
            }
            AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
            record.setCarrier(carrier);
            record.setRecordType(parseEnum(row.getRecordType(), AtemschutzFitnessType.class, AtemschutzFitnessType.UEBUNG));
            record.setValidFrom(parseDate(row.getValidFrom()));
            record.setValidUntil(parseDateRequired(row.getValidUntil(), "valid_until"));
            record.setPhysician(row.getPhysician());
            record.setResultNotes(row.getResultNotes());
            record.setSourceLabel(row.getSourceLabel());
            record.setTestData(testData);
            fitnessRecordRepository.save(record);
            summary.fitnessRecords++;
        }
        if (doc.getAtemschutzSettings() != null) {
            UnitAtemschutzSettings settings = atemschutzSettingsService.ensureSettings(unitId);
            UnitDataExportDocument.AtemschutzSettingsRow s = doc.getAtemschutzSettings();
            if (s.getWarnDays() != null) {
                settings.setWarnDays(s.getWarnDays());
            }
            if (s.getAgtCourseName() != null) {
                settings.setAgtCourseName(s.getAgtCourseName());
            }
            if (s.getAgtCourseSourceId() != null) {
                Course agt = courses.get(s.getAgtCourseSourceId());
                settings.setAgtCourse(agt);
            }
            settings.setNotificationUserIds(s.getNotificationUserIds());
            settings.setCcUserIds(s.getCcUserIds());
            atemschutzSettingsRepository.save(settings);
            summary.settingsImported = true;
        }
        for (UnitDataExportDocument.EmailTemplateRow row : doc.getAtemschutzEmailTemplates()) {
            AtemschutzEmailTemplate tpl = emailTemplateRepository
                    .findByUnitIdAndTemplateKey(unitId, row.getTemplateKey())
                    .orElseGet(() -> {
                        AtemschutzEmailTemplate created = new AtemschutzEmailTemplate();
                        created.setUnit(unit);
                        created.setTemplateKey(row.getTemplateKey());
                        return created;
                    });
            tpl.setTemplateName(row.getTemplateName());
            tpl.setSubject(row.getSubject());
            tpl.setBody(row.getBody());
            emailTemplateRepository.save(tpl);
            summary.emailTemplates++;
        }
        return summary;
    }

    private Map<Long, QualificationType> importLegacyQualifications(
            Unit unit,
            Map<String, List<Map<String, String>>> tables,
            Integer sourceEinheitId,
            boolean testData,
            ImportSummary summary) {
        Map<Long, QualificationType> result = new HashMap<>();
        for (Map<String, String> row : tables.getOrDefault("member_qualifications", List.of())) {
            if (!matchesEinheit(row, sourceEinheitId)) {
                continue;
            }
            long sourceId = parseLong(row.get("id"));
            String name = row.get("name");
            if (blank(name)) {
                continue;
            }
            QualificationType qt = findOrCreateQualification(unit, testData, sourceId, name.trim());
            qt.setSortOrder(parseInt(row.get("sort_order"), qt.getSortOrder()));
            result.put(sourceId, qualificationTypeRepository.save(qt));
            summary.qualifications++;
        }
        return result;
    }

    private Map<Long, Course> importLegacyCourses(
            Unit unit,
            Map<String, List<Map<String, String>>> tables,
            Integer sourceEinheitId,
            boolean testData,
            Map<Long, QualificationType> qualificationBySource,
            ImportSummary summary) {
        Map<Long, Course> result = new HashMap<>();
        for (Map<String, String> row : tables.getOrDefault("courses", List.of())) {
            if (!matchesEinheit(row, sourceEinheitId)) {
                continue;
            }
            long sourceId = parseLong(row.get("id"));
            String name = row.get("name");
            if (blank(name)) {
                continue;
            }
            Course course = findOrCreateCourse(unit, testData, sourceId, name.trim());
            Long qualId = parseLongOrNull(row.get("qualification_id"));
            if (qualId != null) {
                course.setQualificationType(qualificationBySource.get(qualId));
            }
            result.put(sourceId, courseRepository.save(course));
            summary.courses++;
        }
        return result;
    }

    private Map<Long, Person> importLegacyMembers(
            Unit unit,
            Map<String, List<Map<String, String>>> tables,
            Integer sourceEinheitId,
            boolean testData,
            Map<Long, QualificationType> qualificationBySource,
            ImportSummary summary) {
        Map<Long, Person> result = new HashMap<>();
        for (Map<String, String> row : tables.getOrDefault("members", List.of())) {
            if (!matchesEinheit(row, sourceEinheitId)) {
                continue;
            }
            long sourceId = parseLong(row.get("id"));
            Person person = findOrCreatePerson(unit, testData, sourceId);
            person.setFirstName(required(row.get("first_name"), "first_name"));
            person.setLastName(required(row.get("last_name"), "last_name"));
            person.setEmail(row.get("email"));
            person.setPhone(row.get("phone"));
            person.setBirthdate(parseDate(row.get("birthdate")));
            person.setStatus(PersonStatus.ACTIVE);
            Long qualId = parseLongOrNull(row.get("qualification_id"));
            if (qualId != null) {
                person.setQualificationType(qualificationBySource.get(qualId));
            }
            if (row.get("divera_ucr_id") != null) {
                person.setDiveraUcrId(row.get("divera_ucr_id").trim());
            }
            result.put(sourceId, personRepository.save(person));
            summary.persons++;
        }
        return result;
    }

    private void importLegacyMemberCourses(
            Map<String, List<Map<String, String>>> tables,
            Map<Long, Person> personByMemberId,
            Map<Long, Course> courseBySource,
            ImportSummary summary) {
        for (Map<String, String> row : tables.getOrDefault("member_courses", List.of())) {
            Long memberId = parseLongOrNull(row.get("member_id"));
            Long courseId = parseLongOrNull(row.get("course_id"));
            if (memberId == null || courseId == null) {
                continue;
            }
            Person person = personByMemberId.get(memberId);
            Course course = courseBySource.get(courseId);
            if (person == null || course == null) {
                continue;
            }
            if (personCourseCompletionRepository.existsByPersonIdAndCourseId(person.getId(), course.getId())) {
                continue;
            }
            PersonCourseCompletion cc = new PersonCourseCompletion();
            cc.setPerson(person);
            cc.setCourse(course);
            LocalDate completed = parseDate(row.get("completed_date"));
            cc.setCompletedOn(completed);
            if (completed != null) {
                cc.setCompletionYear(completed.getYear());
            }
            personCourseCompletionRepository.save(cc);
            summary.courseCompletions++;
        }
    }

    private void importLegacyGroups(
            Unit unit,
            Map<String, List<Map<String, String>>> tables,
            Integer sourceEinheitId,
            boolean testData,
            Map<Long, Person> personByMemberId,
            ImportSummary summary) {
        Map<Long, PersonGroup> groupsBySource = new HashMap<>();
        for (Map<String, String> row : tables.getOrDefault("member_groups", List.of())) {
            if (!matchesEinheit(row, sourceEinheitId)) {
                continue;
            }
            long sourceId = parseLong(row.get("id"));
            String name = row.get("group_name");
            if (blank(name)) {
                name = row.get("name");
            }
            if (blank(name)) {
                continue;
            }
            PersonGroup group = personGroupRepository
                    .findByUnitIdWithMembers(unit.getId(), testData).stream()
                    .filter(g -> g.getName().equalsIgnoreCase(name.trim()))
                    .findFirst()
                    .orElseGet(() -> {
                        PersonGroup created = new PersonGroup();
                        created.setUnit(unit);
                        created.setName(name.trim());
                        created.setTestData(testData);
                        return created;
                    });
            group.getMembers().clear();
            groupsBySource.put(sourceId, personGroupRepository.save(group));
            summary.groups++;
        }
        for (Map<String, String> row : tables.getOrDefault("member_group_members", List.of())) {
            Long groupId = parseLongOrNull(row.get("group_id"));
            Long memberId = parseLongOrNull(row.get("member_id"));
            if (groupId == null || memberId == null) {
                continue;
            }
            PersonGroup group = groupsBySource.get(groupId);
            Person person = personByMemberId.get(memberId);
            if (group == null || person == null) {
                continue;
            }
            if (!group.getMembers().contains(person)) {
                group.getMembers().add(person);
            }
        }
        groupsBySource.values().forEach(personGroupRepository::save);
    }

    private void importLegacyAtemschutz(
            Unit unit,
            Map<String, List<Map<String, String>>> tables,
            Integer sourceEinheitId,
            boolean testData,
            Map<Long, Person> personByMemberId,
            ImportSummary summary) {
        for (Map<String, String> row : tables.getOrDefault("atemschutz_traeger", List.of())) {
            if (!matchesEinheit(row, sourceEinheitId)) {
                continue;
            }
            Person person = resolveLegacyPerson(row, personByMemberId);
            if (person == null) {
                continue;
            }
            AtemschutzCarrier carrier = atemschutzCarrierRepository.findByUnitId(unit.getId(), testData).stream()
                    .filter(c -> c.getPerson().getId().equals(person.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        AtemschutzCarrier created = new AtemschutzCarrier();
                        created.setUnit(unit);
                        created.setPerson(person);
                        created.setTestData(testData);
                        return created;
                    });
            carrier.setStatus(mapLegacyCarrierStatus(row.get("status")));
            atemschutzCarrierRepository.save(carrier);
            summary.carriers++;

            createLegacyFitnessRecord(carrier, testData, AtemschutzFitnessType.G26_UNTERSUCHUNG, row.get("g263_am"), person.getBirthdate(), summary);
            createLegacyFitnessRecord(carrier, testData, AtemschutzFitnessType.UEBUNG, row.get("uebung_am"), person.getBirthdate(), summary);
            createLegacyFitnessRecord(carrier, testData, AtemschutzFitnessType.STRECKEN, row.get("strecke_am"), person.getBirthdate(), summary);
        }
    }

    private void createLegacyFitnessRecord(
            AtemschutzCarrier carrier,
            boolean testData,
            AtemschutzFitnessType type,
            String dateValue,
            LocalDate birthdate,
            ImportSummary summary) {
        LocalDate validFrom = parseDate(dateValue);
        if (validFrom == null) {
            return;
        }
        AtemschutzFitnessRecord record = new AtemschutzFitnessRecord();
        record.setCarrier(carrier);
        record.setRecordType(type);
        record.setValidFrom(validFrom);
        record.setValidUntil(AtemschutzService.computeValidUntil(type, validFrom, birthdate));
        record.setTestData(testData);
        fitnessRecordRepository.save(record);
        summary.fitnessRecords++;
    }

    private Person resolveLegacyPerson(Map<String, String> row, Map<Long, Person> personByMemberId) {
        Long memberId = parseLongOrNull(row.get("member_id"));
        if (memberId != null && personByMemberId.containsKey(memberId)) {
            return personByMemberId.get(memberId);
        }
        String first = row.get("first_name");
        String last = row.get("last_name");
        LocalDate birthdate = parseDate(row.get("birthdate"));
        if (blank(first) || blank(last)) {
            return null;
        }
        return personByMemberId.values().stream()
                .filter(p -> p.getFirstName().equalsIgnoreCase(first.trim())
                        && p.getLastName().equalsIgnoreCase(last.trim())
                        && (birthdate == null || Objects.equals(p.getBirthdate(), birthdate)))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void clearUnitPersonalAndAtemschutz(long unitId) {
        boolean testData = testModeService.isEnabled();
        entityManager
                .createQuery(
                        "DELETE FROM AtemschutzFitnessRecord r WHERE r.carrier.unit.id = :unitId AND r.testData = :testData")
                .setParameter("unitId", unitId)
                .setParameter("testData", testData)
                .executeUpdate();
        entityManager
                .createQuery("DELETE FROM AtemschutzCarrier c WHERE c.unit.id = :unitId AND c.testData = :testData")
                .setParameter("unitId", unitId)
                .setParameter("testData", testData)
                .executeUpdate();
        emailTemplateRepository.findByUnitIdOrderByTemplateKeyAsc(unitId).forEach(emailTemplateRepository::delete);
        atemschutzSettingsRepository.findByUnitId(unitId).ifPresent(atemschutzSettingsRepository::delete);

        List<PersonGroup> groups = personGroupRepository.findByUnitIdWithMembers(unitId, testData);
        for (PersonGroup group : groups) {
            group.getMembers().clear();
            personGroupRepository.save(group);
        }
        personGroupRepository.deleteAll(groups);

        List<Person> persons = personRepository.findActiveByUnitId(unitId, testData);
        List<Person> deletablePersons = persons.stream().filter(p -> !isProtectedPerson(p)).toList();
        for (Person person : deletablePersons) {
            personCourseCompletionRepository.deleteByPersonId(person.getId());
            personAttendanceRepository.deleteAll(
                    personAttendanceRepository.findByPersonIdOrderByServiceDateDesc(person.getId()));
            personDiveraRicRepository.deleteByPersonId(person.getId());
        }
        personRepository.deleteAll(deletablePersons);

        List<Course> courses = courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testData);
        courseRepository.deleteAll(courses);
        List<QualificationType> qualifications =
                qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testData);
        qualificationTypeRepository.deleteAll(qualifications);
    }

    private QualificationType findOrCreateQualification(Unit unit, boolean testData, long sourceId, String name) {
        return qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unit.getId(), testData).stream()
                .filter(q -> sourceId > 0 && Objects.equals(q.getProductionSourceId(), sourceId))
                .findFirst()
                .or(() -> qualificationTypeRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unit.getId(), testData).stream()
                        .filter(q -> q.getName().equalsIgnoreCase(name))
                        .findFirst())
                .orElseGet(() -> {
                    QualificationType created = new QualificationType();
                    created.setUnit(unit);
                    created.setName(name);
                    created.setTestData(testData);
                    created.setProductionSourceId(sourceId > 0 ? sourceId : null);
                    return created;
                });
    }

    private Course findOrCreateCourse(Unit unit, boolean testData, long sourceId, String name) {
        return courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unit.getId(), testData).stream()
                .filter(c -> sourceId > 0 && Objects.equals(c.getProductionSourceId(), sourceId))
                .findFirst()
                .or(() -> courseRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unit.getId(), testData).stream()
                        .filter(c -> c.getName().equalsIgnoreCase(name))
                        .findFirst())
                .orElseGet(() -> {
                    Course created = new Course();
                    created.setUnit(unit);
                    created.setName(name);
                    created.setTestData(testData);
                    created.setProductionSourceId(sourceId > 0 ? sourceId : null);
                    return created;
                });
    }

    private Person findOrCreatePerson(Unit unit, boolean testData, long sourceId) {
        return personRepository.findActiveByUnitId(unit.getId(), testData).stream()
                .filter(p -> sourceId > 0 && Objects.equals(p.getProductionSourceId(), sourceId))
                .findFirst()
                .orElseGet(() -> {
                    Person created = new Person();
                    created.setUnit(unit);
                    created.setTestData(testData);
                    created.setProductionSourceId(sourceId > 0 ? sourceId : null);
                    created.setFirstName("—");
                    created.setLastName("—");
                    return created;
                });
    }

    private static void applyPersonRow(Person person, UnitDataExportDocument.PersonRow row) {
        person.setFirstName(required(row.getFirstName(), "first_name"));
        person.setLastName(required(row.getLastName(), "last_name"));
        person.setEmail(row.getEmail());
        person.setEmailPrivate(row.getEmailPrivate());
        person.setPhone(row.getPhone());
        person.setAddress(row.getAddress());
        person.setBirthdate(parseDate(row.getBirthdate()));
        person.setStatus(parseEnum(row.getStatus(), PersonStatus.class, PersonStatus.ACTIVE));
        person.setDiveraUcrId(row.getDiveraUcrId());
        person.setNotes(row.getNotes());
        person.setPersonnelNumber(row.getPersonnelNumber());
        person.setEntryDate(parseDate(row.getEntryDate()));
        person.setExitDate(parseDate(row.getExitDate()));
    }

    private static UnitDataExportDocument.PersonRow toPersonRow(Person person, long sourceId) {
        UnitDataExportDocument.PersonRow row = new UnitDataExportDocument.PersonRow();
        row.setSourceId(sourceId);
        row.setFirstName(person.getFirstName());
        row.setLastName(person.getLastName());
        row.setEmail(person.getEmail());
        row.setEmailPrivate(person.getEmailPrivate());
        row.setPhone(person.getPhone());
        row.setAddress(person.getAddress());
        row.setBirthdate(formatDate(person.getBirthdate()));
        row.setStatus(person.getStatus().name());
        row.setDiveraUcrId(person.getDiveraUcrId());
        row.setNotes(person.getNotes());
        row.setPersonnelNumber(person.getPersonnelNumber());
        row.setEntryDate(formatDate(person.getEntryDate()));
        row.setExitDate(formatDate(person.getExitDate()));
        if (person.getQualificationType() != null) {
            row.setQualificationTypeSourceId(sourceId(
                    person.getQualificationType().getProductionSourceId(), person.getQualificationType().getId()));
        }
        return row;
    }

    private Unit requireUnit(long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private static long sourceId(Long productionSourceId, long id) {
        return productionSourceId != null ? productionSourceId : id;
    }

    /** Personen mit verknüpftem Superadmin- oder Einheitsadmin-Konto nicht löschen. */
    private static boolean isProtectedPerson(Person person) {
        if (person.getUser() == null) {
            return false;
        }
        UserRole role = person.getUser().getRole();
        return role == UserRole.SUPER_ADMIN || role == UserRole.UNIT_ADMIN;
    }

    private static boolean matchesEinheit(Map<String, String> row, Integer sourceEinheitId) {
        if (sourceEinheitId == null) {
            return true;
        }
        String einheit = row.get("einheit_id");
        if (einheit == null) {
            einheit = row.get("unit_id");
        }
        return Integer.toString(sourceEinheitId).equals(einheit);
    }

    private static AtemschutzCarrierStatus mapLegacyCarrierStatus(String status) {
        if (status != null && status.toLowerCase(Locale.ROOT).contains("aktiv")) {
            return AtemschutzCarrierStatus.ACTIVE;
        }
        return AtemschutzCarrierStatus.PAUSED;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private static LocalDate parseDate(String value) {
        if (blank(value) || "0000-00-00".equals(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate parseDateRequired(String value, String field) {
        LocalDate date = parseDate(value);
        if (date == null) {
            throw new IllegalArgumentException("Ungültiges Datum für " + field + ".");
        }
        return date;
    }

    private static long parseLong(String value) {
        if (blank(value)) {
            return 0L;
        }
        return Long.parseLong(value.trim());
    }

    private static Long parseLongOrNull(String value) {
        if (blank(value)) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static int parseInt(String value, int fallback) {
        if (blank(value)) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String required(String value, String field) {
        if (blank(value)) {
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + field);
        }
        return value.trim();
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (blank(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static class ImportSummary {
        public int qualifications;
        public int courses;
        public int persons;
        public int courseCompletions;
        public int groups;
        public int attendance;
        public int rics;
        public int carriers;
        public int fitnessRecords;
        public int emailTemplates;
        public boolean settingsImported;

        public String message() {
            return String.format(
                    Locale.GERMANY,
                    "%d Qualifikationen, %d Lehrgänge, %d Personen, %d Lehrgangszuordnungen, %d Gruppen, "
                            + "%d Geräteträger, %d Nachweise importiert.",
                    qualifications,
                    courses,
                    persons,
                    courseCompletions,
                    groups,
                    carriers,
                    fitnessRecords);
        }
    }
}
