package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookOutcome;
import de.feuerwehr.manager.divera.DiveraWebhookService.WebhookStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestDiveraAlarmService {

    private final TestDiveraAlarmRepository repository;
    private final UnitRepository unitRepository;
    private final TestModeService testModeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookOutcome startEinsatzFromPayload(long unitId, String rawBody) {
        if (!testModeService.isEnabled()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Testmodus ist nicht aktiv");
        }
        if (!unitRepository.existsById(unitId)) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Unbekannte Einheit");
        }
        if (rawBody == null || rawBody.isBlank()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "JSON fehlt");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            Optional<DiveraAlarmJsonParser.ParsedAlarm> parsed = DiveraAlarmJsonParser.parseFirst(root);
            if (parsed.isEmpty()) {
                return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Kein Alarm im JSON erkannt");
            }
            DiveraAlarmJsonParser.ParsedAlarm p = parsed.get();
            if (repository.findByUnitIdAndAlarmIdAndClosedFalse(unitId, p.alarmId()).isPresent()) {
                return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Dieser Einsatz läuft bereits");
            }
            TestDiveraAlarm saved = saveParsed(unitId, asOpenAlarm(p));
            return new WebhookOutcome(
                    WebhookStatus.ACCEPTED,
                    saved.getExternalId() != null ? saved.getExternalId() : "test:" + saved.getAlarmId(),
                    "Einsatz gestartet — sichtbar auf der Startseite");
        } catch (Exception e) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Ungültiges JSON: " + e.getMessage());
        }
    }

    @Transactional
    public WebhookOutcome ingestTestWebhook(long unitId, String rawBody) {
        if (!testModeService.isEnabled()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Testmodus ist nicht aktiv");
        }
        if (!unitRepository.existsById(unitId)) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Unbekannte Einheit");
        }
        if (rawBody == null || rawBody.isBlank()) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "JSON fehlt");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            Optional<DiveraAlarmJsonParser.ParsedAlarm> parsed = DiveraAlarmJsonParser.parseFirst(root);
            if (parsed.isEmpty()) {
                return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Kein Alarm im JSON erkannt");
            }
            TestDiveraAlarm saved = saveParsed(unitId, parsed.get());
            String ext = saved.getExternalId() != null ? saved.getExternalId() : "test:" + saved.getAlarmId();
            String msg = saved.isClosed()
                    ? "Webhook verarbeitet (Einsatz geschlossen — nicht auf der Startseite)"
                    : "Testalarm gespeichert — erscheint auf der Startseite";
            return new WebhookOutcome(WebhookStatus.ACCEPTED, ext, msg);
        } catch (Exception e) {
            return new WebhookOutcome(WebhookStatus.BAD_REQUEST, null, "Ungültiges JSON: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> listOpenSummariesForUnit(long unitId) {
        if (!testModeService.isEnabled()) {
            return List.of();
        }
        return repository.findByUnitIdAndClosedFalseOrderByCreatedAtDesc(unitId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TestDiveraAlarm> listOpenForUnit(long unitId) {
        if (!testModeService.isEnabled()) {
            return List.of();
        }
        return repository.findByUnitIdAndClosedFalseOrderByCreatedAtDesc(unitId);
    }

    @Transactional
    public void closeAlarm(long unitId, long testRecordId) {
        if (!testModeService.isEnabled()) {
            throw new IllegalArgumentException("Testmodus ist nicht aktiv");
        }
        TestDiveraAlarm alarm = repository
                .findById(testRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Testalarm nicht gefunden"));
        if (alarm.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Testalarm gehört nicht zu dieser Einheit");
        }
        if (alarm.isClosed()) {
            return;
        }
        alarm.setClosed(true);
        alarm.setClosedAt(Instant.now());
        repository.save(alarm);
    }

    @Transactional(readOnly = true)
    public boolean isAlarmRunning(long unitId, long diveraAlarmId) {
        if (!testModeService.isEnabled()) {
            return false;
        }
        return repository.findByUnitIdAndAlarmIdAndClosedFalse(unitId, diveraAlarmId).isPresent();
    }

    private static DiveraAlarmJsonParser.ParsedAlarm asOpenAlarm(DiveraAlarmJsonParser.ParsedAlarm p) {
        return new DiveraAlarmJsonParser.ParsedAlarm(
                p.alarmId(),
                p.externalId(),
                p.title(),
                p.text(),
                p.address(),
                p.dateEpochSeconds(),
                p.tsCreateSeconds(),
                false);
    }

    public List<DiveraAlarmSummary> mergeInto(DiveraAlarmsResponse apiResponse, List<DiveraAlarmSummary> testAlarms) {
        if (testAlarms.isEmpty()) {
            return apiResponse.alarms();
        }
        if (!apiResponse.success()) {
            return sortedCopy(testAlarms);
        }
        List<DiveraAlarmSummary> merged = new ArrayList<>(apiResponse.alarms());
        merged.addAll(testAlarms);
        return sortedCopy(merged);
    }

    private static List<DiveraAlarmSummary> sortedCopy(List<DiveraAlarmSummary> alarms) {
        return alarms.stream()
                .sorted(Comparator.comparingLong(DiveraAlarmSummary::dateEpochSeconds).reversed())
                .toList();
    }

    private TestDiveraAlarm saveParsed(long unitId, DiveraAlarmJsonParser.ParsedAlarm p) {
        Unit unit = unitRepository.getReferenceById(unitId);
        TestDiveraAlarm entity = new TestDiveraAlarm();
        entity.setUnit(unit);
        entity.setAlarmId(p.alarmId());
        entity.setExternalId(blankToNull(p.externalId()));
        entity.setTitle(p.title());
        entity.setAlarmText(blankToNull(p.text()));
        entity.setAddress(blankToNull(p.address()));
        entity.setDateEpochSeconds(p.dateEpochSeconds());
        entity.setTsCreateSeconds(p.tsCreateSeconds());
        entity.setClosed(p.closed());
        entity.setCreatedAt(Instant.now());
        if (p.closed()) {
            entity.setClosedAt(Instant.now());
        }
        return repository.save(entity);
    }

    private DiveraAlarmSummary toSummary(TestDiveraAlarm a) {
        return DiveraAlarmSummary.fromTestAlarm(
                a.getAlarmId(),
                a.getId(),
                a.getTitle(),
                a.getAlarmText(),
                a.getAddress(),
                a.getDateEpochSeconds(),
                a.getTsCreateSeconds(),
                a.isClosed());
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
