package de.feuerwehr.manager.atemschutz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
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
        return settingsRepository.findByUnitId(unitId).orElseGet(() -> {
            Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden"));
            UnitAtemschutzSettings settings = new UnitAtemschutzSettings();
            settings.setUnit(unit);
            settings.setWarnDays(globalSettingsService.get().getQualificationWarnDays());
            settings.setAgtCourseName("AGT");
            UnitAtemschutzSettings saved = settingsRepository.save(settings);
            seedEmailTemplates(unit);
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public int warnDays(long unitId) {
        return requireSettings(unitId).getWarnDays();
    }

    @Transactional(readOnly = true)
    public String agtCourseName(long unitId) {
        String name = requireSettings(unitId).getAgtCourseName();
        return name == null || name.isBlank() ? "AGT" : name.trim();
    }

    @Transactional
    public void saveWarnschwelle(long unitId, int warnDays, String agtCourseName) {
        if (warnDays < 0) {
            throw new IllegalArgumentException("Warnschwelle darf nicht negativ sein.");
        }
        String course = agtCourseName == null || agtCourseName.isBlank() ? "AGT" : agtCourseName.trim();
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        settings.setWarnDays(warnDays);
        settings.setAgtCourseName(course);
        settingsRepository.save(settings);
    }

    @Transactional
    public void saveNotificationUsers(long unitId, List<Long> userIds) {
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        settings.setNotificationUserIds(writeIds(userIds));
        settingsRepository.save(settings);
    }

    @Transactional
    public void saveCcUsers(long unitId, List<Long> userIds) {
        UnitAtemschutzSettings settings = ensureSettings(unitId);
        settings.setCcUserIds(writeIds(userIds));
        settingsRepository.save(settings);
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

    private UnitAtemschutzSettings buildDefaultSettings(long unitId) {
        UnitAtemschutzSettings settings = new UnitAtemschutzSettings();
        settings.setWarnDays(globalSettingsService.get().getQualificationWarnDays());
        settings.setAgtCourseName("AGT");
        return settings;
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
