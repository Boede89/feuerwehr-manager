package de.feuerwehr.manager.atemschutz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.Course;
import de.feuerwehr.manager.personal.CourseRepository;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtemschutzSettingsService {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};

    private final UnitRepository unitRepository;
    private final UnitAtemschutzSettingsRepository settingsRepository;
    private final AtemschutzEmailTemplateRepository emailTemplateRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PersonalService personalService;
    private final GlobalSettingsService globalSettingsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UnitAtemschutzSettings requireSettings(long unitId) {
        return settingsRepository
                .findByUnitId(unitId)
                .orElseGet(() -> buildDefaultSettings(unitId));
    }

    @Transactional
    public UnitAtemschutzSettings ensureSettings(long unitId) {
        return settingsRepository.findByUnitId(unitId).map(settings -> {
            if (settings.getAgtCourse() == null) {
                resolveDefaultAgtCourse(unitId, settings);
                if (settings.getAgtCourse() != null) {
                    return settingsRepository.save(settings);
                }
            }
            return settings;
        }).orElseGet(() -> {
            Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
            UnitAtemschutzSettings settings = new UnitAtemschutzSettings();
            settings.setUnit(unit);
            int defaultWarn = globalSettingsService.get().getQualificationWarnDays();
            settings.setWarnDays(defaultWarn);
            settings.setG26WarnDays(defaultWarn);
            settings.setStreckeWarnDays(defaultWarn);
            settings.setUebungWarnDays(defaultWarn);
            settings.setAgtCourseName("AGT");
            resolveDefaultAgtCourse(unitId, settings);
            UnitAtemschutzSettings saved = settingsRepository.save(settings);
            seedEmailTemplates(unit);
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Long selectedAgtCourseUiId(long unitId) {
        return settingsRepository
                .findByUnitId(unitId)
                .map(UnitAtemschutzSettings::getAgtCourse)
                .flatMap(stored -> personalService.listCourses(unitId, true).stream()
                        .filter(course -> matchesStoredCourse(course, normalizeStoredCourseId(stored)))
                        .map(Course::getId)
                        .findFirst())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Course> listSelectableCourses(long unitId) {
        return personalService.listCourses(unitId, true);
    }

    @Transactional(readOnly = true)
    public int warnDays(long unitId) {
        return requireSettings(unitId).getStreckeWarnDays();
    }

    @Transactional(readOnly = true)
    public int warnDays(long unitId, AtemschutzFitnessType type) {
        UnitAtemschutzSettings settings = requireSettings(unitId);
        return switch (type) {
            case G26_UNTERSUCHUNG -> settings.getG26WarnDays();
            case STRECKEN -> settings.getStreckeWarnDays();
            case UEBUNG -> settings.getUebungWarnDays();
        };
    }

    @Transactional(readOnly = true)
    public Optional<Long> agtCourseId(long unitId) {
        UnitAtemschutzSettings settings = requireSettings(unitId);
        if (settings.getAgtCourse() == null) {
            return Optional.empty();
        }
        return Optional.of(normalizeStoredCourseId(settings.getAgtCourse()));
    }

    @Transactional(readOnly = true)
    public String agtCourseName(long unitId) {
        UnitAtemschutzSettings settings = requireSettings(unitId);
        if (settings.getAgtCourse() != null) {
            return settings.getAgtCourse().getName();
        }
        String name = settings.getAgtCourseName();
        return name == null || name.isBlank() ? "—" : name.trim();
    }

    @Transactional(readOnly = true)
    public boolean isAgtCourseConfigured(long unitId) {
        return agtCourseId(unitId).isPresent();
    }

    @Transactional
    public void saveAgtCourse(long unitId, Long agtCourseId) {
        if (agtCourseId == null || agtCourseId <= 0) {
            throw new IllegalArgumentException("Bitte einen Lehrgang auswählen.");
        }
        Course selected = courseRepository
                .findById(agtCourseId)
                .orElseThrow(() -> new IllegalArgumentException("Lehrgang nicht gefunden."));
        if (!selected.getUnit().getId().equals(unitId)) {
            throw new IllegalArgumentException("Lehrgang gehört nicht zur Einheit.");
        }
        Course stored = resolveStoredCourse(selected);
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        settings.setAgtCourse(stored);
        settings.setAgtCourseName(stored.getName());
        settingsRepository.save(settings);
    }

    @Transactional
    public void saveInstructors(long unitId, List<Long> userIds) {
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        settings.setInstructorUserIds(writeIds(userIds));
        settingsRepository.save(settings);
    }

    @Transactional
    public void saveNotificationSettings(
            long unitId,
            int g26WarnDays,
            int streckeWarnDays,
            int uebungWarnDays,
            boolean g26NotifyInstructors,
            boolean streckeNotifyInstructors,
            boolean uebungNotifyInstructors,
            List<Long> g26CcUserIds,
            List<Long> streckeCcUserIds,
            List<Long> uebungCcUserIds,
            Map<String, String> templateSubjects,
            Map<String, String> templateBodies) {
        validateWarnDays(g26WarnDays);
        validateWarnDays(streckeWarnDays);
        validateWarnDays(uebungWarnDays);
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        settings.setG26WarnDays(g26WarnDays);
        settings.setStreckeWarnDays(streckeWarnDays);
        settings.setUebungWarnDays(uebungWarnDays);
        settings.setWarnDays(streckeWarnDays);
        settings.setG26NotifyInstructors(g26NotifyInstructors);
        settings.setStreckeNotifyInstructors(streckeNotifyInstructors);
        settings.setUebungNotifyInstructors(uebungNotifyInstructors);
        settings.setG26CcUserIds(writeIds(g26CcUserIds));
        settings.setStreckeCcUserIds(writeIds(streckeCcUserIds));
        settings.setUebungCcUserIds(writeIds(uebungCcUserIds));
        settingsRepository.save(settings);
        if (templateSubjects != null && templateBodies != null) {
            for (AtemschutzNotificationCategory category : AtemschutzNotificationCategory.values()) {
                saveTemplateIfPresent(unitId, category.getWarnungTemplateKey(), templateSubjects, templateBodies);
                saveTemplateIfPresent(unitId, category.getAbgelaufenTemplateKey(), templateSubjects, templateBodies);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AtemschutzNotificationSectionView> buildNotificationSections(long unitId) {
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        Map<String, AtemschutzEmailTemplate> templatesByKey = listEmailTemplates(unitId).stream()
                .collect(java.util.stream.Collectors.toMap(AtemschutzEmailTemplate::getTemplateKey, t -> t, (a, b) -> a));
        List<AtemschutzNotificationSectionView> sections = new ArrayList<>();
        for (AtemschutzNotificationCategory category : AtemschutzNotificationCategory.values()) {
            sections.add(new AtemschutzNotificationSectionView(
                    category,
                    readWarnDays(settings, category),
                    readNotifyInstructors(settings, category),
                    readCcUserIds(settings, category),
                    requireTemplate(templatesByKey, category.getWarnungTemplateKey()),
                    requireTemplate(templatesByKey, category.getAbgelaufenTemplateKey())));
        }
        return sections;
    }

    @Transactional
    public void saveEmailTemplate(long unitId, String templateKey, String subject, String body) {
        AtemschutzEmailTemplate template = emailTemplateRepository
                .findByUnitIdAndTemplateKey(unitId, templateKey)
                .orElseThrow(() -> new IllegalArgumentException("E-Mail-Vorlage nicht gefunden."));
        template.setSubject(subject == null ? "" : subject.trim());
        template.setBody(body == null ? "" : body.trim());
        emailTemplateRepository.save(template);
    }

    @Transactional
    public List<AtemschutzEmailTemplate> listEmailTemplates(long unitId) {
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        List<AtemschutzEmailTemplate> templates = emailTemplateRepository.findByUnitIdOrderByTemplateKeyAsc(unitId);
        if (templates.isEmpty()) {
            seedEmailTemplates(settings.getUnit());
            templates = emailTemplateRepository.findByUnitIdOrderByTemplateKeyAsc(unitId);
        }
        return templates;
    }

    @Transactional(readOnly = true)
    public List<User> listSelectableUnitUsers(long unitId) {
        return userRepository.findAllByAnonymizedAtIsNullAndUnitIdOrderByUsernameAsc(unitId);
    }

    @Transactional(readOnly = true)
    public List<Long> parseNotificationUserIds(UnitAtemschutzSettings settings) {
        return parseIds(settings.getNotificationUserIds());
    }

    @Transactional(readOnly = true)
    public List<Long> parseCcUserIds(UnitAtemschutzSettings settings) {
        return parseIds(settings.getCcUserIds());
    }

    @Transactional(readOnly = true)
    public List<Long> parseInstructorUserIds(UnitAtemschutzSettings settings) {
        return parseIds(settings.getInstructorUserIds());
    }

    private static void validateWarnDays(int warnDays) {
        if (warnDays < 0 || warnDays > 365) {
            throw new IllegalArgumentException("Warnschwelle muss zwischen 0 und 365 Tagen liegen.");
        }
    }

    private void saveTemplateIfPresent(
            long unitId, String templateKey, Map<String, String> subjects, Map<String, String> bodies) {
        String subject = subjects.get(templateKey);
        String body = bodies.get(templateKey);
        if (subject == null || body == null) {
            return;
        }
        saveEmailTemplate(unitId, templateKey, subject, body);
    }

    private static int readWarnDays(UnitAtemschutzSettings settings, AtemschutzNotificationCategory category) {
        return switch (category) {
            case G26 -> settings.getG26WarnDays();
            case STRECKEN -> settings.getStreckeWarnDays();
            case UEBUNG -> settings.getUebungWarnDays();
        };
    }

    private static boolean readNotifyInstructors(UnitAtemschutzSettings settings, AtemschutzNotificationCategory category) {
        return switch (category) {
            case G26 -> settings.isG26NotifyInstructors();
            case STRECKEN -> settings.isStreckeNotifyInstructors();
            case UEBUNG -> settings.isUebungNotifyInstructors();
        };
    }

    private List<Long> readCcUserIds(UnitAtemschutzSettings settings, AtemschutzNotificationCategory category) {
        return switch (category) {
            case G26 -> parseIds(settings.getG26CcUserIds());
            case STRECKEN -> parseIds(settings.getStreckeCcUserIds());
            case UEBUNG -> parseIds(settings.getUebungCcUserIds());
        };
    }

    private static AtemschutzEmailTemplate requireTemplate(
            Map<String, AtemschutzEmailTemplate> templatesByKey, String key) {
        AtemschutzEmailTemplate template = templatesByKey.get(key);
        if (template == null) {
            throw new IllegalStateException("E-Mail-Vorlage fehlt: " + key);
        }
        return template;
    }

    private UnitAtemschutzSettings buildDefaultSettings(long unitId) {
        UnitAtemschutzSettings settings = new UnitAtemschutzSettings();
        int defaultWarn = globalSettingsService.get().getQualificationWarnDays();
        settings.setWarnDays(defaultWarn);
        settings.setG26WarnDays(defaultWarn);
        settings.setStreckeWarnDays(defaultWarn);
        settings.setUebungWarnDays(defaultWarn);
        settings.setAgtCourseName("AGT");
        resolveDefaultAgtCourse(unitId, settings);
        return settings;
    }

    private void resolveDefaultAgtCourse(long unitId, UnitAtemschutzSettings settings) {
        personalService.listCourses(unitId, true).stream()
                .filter(course -> "AGT".equalsIgnoreCase(course.getName().trim()))
                .findFirst()
                .ifPresent(course -> {
                    Course stored = resolveStoredCourse(course);
                    settings.setAgtCourse(stored);
                    settings.setAgtCourseName(stored.getName());
                });
    }

    private Course resolveStoredCourse(Course selected) {
        if (selected.isTestData() && selected.getProductionSourceId() != null) {
            return courseRepository
                    .findById(selected.getProductionSourceId())
                    .orElse(selected);
        }
        if (!selected.isTestData()) {
            return selected;
        }
        return selected;
    }

    private long normalizeStoredCourseId(Course course) {
        if (!course.isTestData()) {
            return course.getId();
        }
        if (course.getProductionSourceId() != null) {
            return course.getProductionSourceId();
        }
        return course.getId();
    }

    private static boolean matchesStoredCourse(Course course, long storedProdId) {
        if (course.getId().equals(storedProdId)) {
            return true;
        }
        return course.getProductionSourceId() != null && course.getProductionSourceId().equals(storedProdId);
    }

    private void seedEmailTemplates(Unit unit) {
        for (TemplateSeed seed : TemplateSeed.defaults()) {
            if (emailTemplateRepository.findByUnitIdAndTemplateKey(unit.getId(), seed.key).isPresent()) {
                continue;
            }
            AtemschutzEmailTemplate template = new AtemschutzEmailTemplate();
            template.setUnit(unit);
            template.setTemplateKey(seed.key);
            template.setTemplateName(seed.name);
            template.setSubject(seed.subject);
            template.setBody(seed.body);
            emailTemplateRepository.save(template);
        }
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, LONG_LIST);
            return ids != null ? ids : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeIds(List<Long> userIds) {
        List<Long> normalized = userIds == null
                ? List.of()
                : userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("Benutzerliste konnte nicht gespeichert werden.");
        }
    }

    private record TemplateSeed(String key, String name, String subject, String body) {
        static List<TemplateSeed> defaults() {
            List<TemplateSeed> seeds = new ArrayList<>();
            seeds.add(new TemplateSeed(
                    "strecke_warnung",
                    "Strecke – Erinnerung (Gelb)",
                    "Erinnerung: Strecke-Zertifikat läuft bald ab",
                    defaultBody("Ihr Strecke-Zertifikat läuft am {expiry_date} ab.", "Verlängerung")));
            seeds.add(new TemplateSeed(
                    "strecke_abgelaufen",
                    "Strecke – Aufforderung (Rot)",
                    "ACHTUNG: Strecke-Zertifikat ist abgelaufen",
                    defaultBody("Ihr Strecke-Zertifikat ist seit dem {expiry_date} abgelaufen!", "SOFORT Verlängerung")));
            seeds.add(new TemplateSeed(
                    "g263_warnung",
                    "G26.3 – Erinnerung (Gelb)",
                    "Erinnerung: G26.3-Untersuchung läuft bald ab",
                    defaultBody("Ihre G26.3-Untersuchung läuft am {expiry_date} ab.", "Termin beim Betriebsarzt")));
            seeds.add(new TemplateSeed(
                    "g263_abgelaufen",
                    "G26.3 – Aufforderung (Rot)",
                    "ACHTUNG: G26.3-Untersuchung ist abgelaufen",
                    defaultBody("Ihre G26.3-Untersuchung ist seit dem {expiry_date} abgelaufen!", "SOFORT Termin beim Betriebsarzt")));
            seeds.add(new TemplateSeed(
                    "uebung_warnung",
                    "Übung/Einsatz – Erinnerung (Gelb)",
                    "Erinnerung: Übung/Einsatz-Zertifikat läuft bald ab",
                    defaultBody("Ihr Übung/Einsatz-Zertifikat läuft am {expiry_date} ab.", "Teilnahme an Übung oder Einsatz")));
            seeds.add(new TemplateSeed(
                    "uebung_abgelaufen",
                    "Übung/Einsatz – Aufforderung (Rot)",
                    "ACHTUNG: Übung/Einsatz-Zertifikat ist abgelaufen",
                    defaultBody("Ihr Übung/Einsatz-Zertifikat ist seit dem {expiry_date} abgelaufen!", "SOFORT Teilnahme")));
            return seeds;
        }

        private static String defaultBody(String main, String action) {
            return "Hallo {first_name} {last_name},\n\n"
                    + main + "\n\n"
                    + "Bitte " + action + ".\n\n"
                    + "Mit freundlichen Grüßen\n"
                    + "Ihre Feuerwehr";
        }
    }
}
