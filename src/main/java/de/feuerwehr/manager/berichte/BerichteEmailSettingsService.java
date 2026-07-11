package de.feuerwehr.manager.berichte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.settings.TestModeService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BerichteEmailSettingsService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final UnitBerichteEmailSettingsRepository repository;
    private final PersonRepository personRepository;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UnitBerichteEmailSettings ensureSettings(long unitId, BerichteEmailReportType reportType) {
        return repository
                .findByUnitIdAndReportType(unitId, reportType)
                .orElseGet(() -> repository.save(createDefaults(unitId, reportType)));
    }

    @Transactional
    public UnitBerichteEmailSettings saveSettings(
            long unitId,
            BerichteEmailReportType reportType,
            boolean emailEnabled,
            IncidentReportStatus sendOnStatus,
            List<Long> personIds,
            List<String> manualEmails) {
        UnitBerichteEmailSettings settings = ensureSettings(unitId, reportType);
        settings.setEmailEnabled(emailEnabled);
        if (reportType.statusTrigger()) {
            settings.setSendOnStatus(sendOnStatus != null ? sendOnStatus : IncidentReportStatus.FREIGEGEBEN);
        } else {
            settings.setSendOnStatus(null);
        }
        settings.setPersonIdsJson(writePersonIds(unitId, personIds));
        settings.setManualEmailsJson(writeManualEmails(manualEmails));
        return repository.save(settings);
    }

    @Transactional(readOnly = true)
    public List<Long> parsePersonIds(UnitBerichteEmailSettings settings) {
        return parseLongList(settings != null ? settings.getPersonIdsJson() : null);
    }

    @Transactional(readOnly = true)
    public List<String> parseManualEmails(UnitBerichteEmailSettings settings) {
        return parseStringList(settings != null ? settings.getManualEmailsJson() : null);
    }

    @Transactional(readOnly = true)
    public List<String> resolveRecipientEmails(long unitId, UnitBerichteEmailSettings settings) {
        if (settings == null || !settings.isEmailEnabled()) {
            return List.of();
        }
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        boolean testData = testModeService.isEnabled();
        for (Long personId : parsePersonIds(settings)) {
            personRepository
                    .findActiveById(personId, testData)
                    .filter(person -> person.getUnit().getId().equals(unitId))
                    .map(BerichteEmailSettingsService::resolvePersonEmail)
                    .filter(email -> !email.isBlank())
                    .ifPresent(emails::add);
        }
        for (String email : parseManualEmails(settings)) {
            if (isValidEmail(email)) {
                emails.add(email.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(emails);
    }

    public static String resolvePersonEmail(Person person) {
        if (person == null) {
            return null;
        }
        if (person.getEmail() != null && !person.getEmail().isBlank()) {
            return person.getEmail().trim();
        }
        if (person.getEmailPrivate() != null && !person.getEmailPrivate().isBlank()) {
            return person.getEmailPrivate().trim();
        }
        if (person.getUser() != null
                && person.getUser().getLoginEmail() != null
                && !person.getUser().getLoginEmail().isBlank()) {
            return person.getUser().getLoginEmail().trim();
        }
        return null;
    }

    public static List<String> parseManualEmailsInput(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\n\\r]+")) {
            String trimmed = part.trim();
            if (isValidEmail(trimmed)) {
                emails.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(emails);
    }

    public int recipientCount(UnitBerichteEmailSettings settings) {
        if (settings == null) {
            return 0;
        }
        return parsePersonIds(settings).size() + parseManualEmails(settings).size();
    }

    private String writePersonIds(long unitId, List<Long> personIds) {
        List<Long> valid = new ArrayList<>();
        boolean testData = testModeService.isEnabled();
        if (personIds != null) {
            for (Long personId : personIds) {
                if (personId == null || personId <= 0) {
                    continue;
                }
                personRepository
                        .findActiveById(personId, testData)
                        .filter(person -> person.getUnit().getId().equals(unitId))
                        .ifPresent(person -> valid.add(personId));
            }
        }
        try {
            return objectMapper.writeValueAsString(valid.stream().distinct().toList());
        } catch (Exception e) {
            return "[]";
        }
    }

    private String writeManualEmails(List<String> manualEmails) {
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        if (manualEmails != null) {
            for (String email : manualEmails) {
                if (isValidEmail(email)) {
                    valid.add(email.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(List.copyOf(valid));
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Long> parseLongList(String json) {
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

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            return values != null ? values : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean isValidEmail(String email) {
        return email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static UnitBerichteEmailSettings createDefaults(long unitId, BerichteEmailReportType reportType) {
        UnitBerichteEmailSettings settings = new UnitBerichteEmailSettings();
        settings.setUnitId(unitId);
        settings.setReportType(reportType);
        settings.setEmailEnabled(false);
        settings.setSendOnStatus(reportType.statusTrigger() ? IncidentReportStatus.FREIGEGEBEN : null);
        settings.setPersonIdsJson("[]");
        settings.setManualEmailsJson("[]");
        return settings;
    }
}
