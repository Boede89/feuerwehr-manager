package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper.DiveraAlarmDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.UnitDiveraSettings;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiveraService {

    private final DiveraApiClient diveraApiClient;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final TestDiveraAlarmService testDiveraAlarmService;
    private final DiveraAlarmSampleService diveraAlarmSampleService;
    private final TestDiveraAlarmRepository testDiveraAlarmRepository;
    private final DiveraAlarmRawJson diveraAlarmRawJson;
    private final ObjectMapper objectMapper;
    private final TestModeService testModeService;
    private final ManualAlarmService manualAlarmService;
    private final ManualAlarmRepository manualAlarmRepository;

    @Transactional(readOnly = true)
    public DiveraAlarmsResponse getAlarmsForUnit(long unitId) {
        List<DiveraAlarmSummary> manualAlarms = manualAlarmService.listActiveSummariesForUnit(unitId);
        if (testModeService.isEnabled()) {
            List<DiveraAlarmSummary> running = testDiveraAlarmService.listOpenSummariesForUnit(unitId);
            return DiveraAlarmsResponse.ok(manualAlarmService.mergeInto(running, manualAlarms));
        }

        DiveraAlarmsResponse apiResponse = diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));

        DiveraAlarmsResponse openApi = withoutClosedAlarms(apiResponse);
        if (!openApi.success()) {
            if (manualAlarms.isEmpty()) {
                return openApi;
            }
            return DiveraAlarmsResponse.ok(manualAlarms);
        }
        return DiveraAlarmsResponse.ok(manualAlarmService.mergeInto(openApi.alarms(), manualAlarms));
    }

    private static DiveraAlarmsResponse withoutClosedAlarms(DiveraAlarmsResponse response) {
        if (!response.success()) {
            return response;
        }
        List<DiveraAlarmSummary> open =
                response.alarms().stream().filter(a -> !a.closed()).toList();
        return DiveraAlarmsResponse.ok(open);
    }

    /**
     * Holt aktuelle DIVERA-Einsätze und speichert sie als Beispiel-Payloads (nur Testmodus).
     * Wird beim Öffnen des Testalarm-Reiters ausgeführt, damit keine Startseite nötig ist.
     */
    @Transactional
    public void syncAlarmSamplesForUnit(long unitId) {
        if (!testModeService.isEnabled()) {
            return;
        }
        diveraSettingsRepository
                .findByUnitId(unitId)
                .ifPresent(cfg -> {
                    DiveraAlarmsResponse apiResponse =
                            diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey());
                    if (apiResponse.success()) {
                        diveraAlarmSampleService.captureFromApiResponse(unitId, apiResponse);
                    }
                });
    }

    /** Nur nicht geschlossene Einsätze (für Dashboard / später Push-Hook). */
    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> getOpenAlarmsForUnit(long unitId) {
        return getAlarmsForUnit(unitId).alarms().stream().filter(a -> !a.closed()).toList();
    }

    /** Alle DIVERA-Einsätze (auch geschlossene) für Einsatzbericht-Sync. */
    @Transactional(readOnly = true)
    public DiveraAlarmsResponse fetchAllAlarmsForBerichteSync(long unitId) {
        if (testModeService.isEnabled()) {
            return fetchAllTestAlarmsForSync(unitId);
        }
        return diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));
    }

    private DiveraAlarmsResponse fetchAllTestAlarmsForSync(long unitId) {
        List<DiveraAlarmSummary> alarms = new ArrayList<>();
        Map<Long, String> rawJsonByAlarmId = new LinkedHashMap<>();
        for (TestDiveraAlarm alarm : testDiveraAlarmRepository.findByUnitIdOrderByCreatedAtDesc(unitId)) {
            alarms.add(DiveraAlarmSummary.fromTestAlarm(
                    alarm.getAlarmId(),
                    alarm.getId(),
                    alarm.getTitle(),
                    alarm.getAlarmText(),
                    alarm.getAddress(),
                    alarm.getDateEpochSeconds(),
                    alarm.getTsCreateSeconds(),
                    alarm.isClosed()));
            rawJsonByAlarmId.put(alarm.getAlarmId(), buildTestAlarmPayload(alarm));
        }
        for (ManualAlarm alarm : manualAlarmRepository.findByUnitIdAndClosedFalseOrderByCreatedAtDesc(unitId)) {
            alarms.add(DiveraAlarmSummary.fromManualAlarm(
                    alarm.getAlarmId(),
                    alarm.getId(),
                    alarm.getTitle(),
                    alarm.getAlarmText(),
                    alarm.getAddress(),
                    alarm.getDateEpochSeconds(),
                    alarm.getTsCreateSeconds(),
                    alarm.isClosed(),
                    alarm.isStarted(),
                    alarm.isExercise()));
            rawJsonByAlarmId.put(alarm.getAlarmId(), buildManualAlarmPayload(alarm));
        }
        return DiveraAlarmsResponse.ok(alarms, rawJsonByAlarmId);
    }

    /** Aktuelle DIVERA-Alarmdetails inkl. ucr_answered (für Personal-Nachzug). */
    @Transactional(readOnly = true)
    public Optional<DiveraAlarmDetails> findAlarmDetailsById(long unitId, long alarmId) {
        if (alarmId <= 0) {
            return Optional.empty();
        }
        Optional<DiveraAlarmDetails> manual = manualAlarmService.findDetails(unitId, alarmId);
        if (manual.isPresent()) {
            return manual;
        }
        DiveraAlarmsResponse response = fetchAllAlarmsForBerichteSync(unitId);
        if (!response.success()) {
            return Optional.empty();
        }
        for (DiveraAlarmSummary summary : response.alarms()) {
            if (summary.id() != alarmId) {
                continue;
            }
            JsonNode root = null;
            String raw = response.rawJsonByAlarmId() != null ? response.rawJsonByAlarmId().get(alarmId) : null;
            if (raw != null && !raw.isBlank()) {
                try {
                    root = objectMapper.readTree(raw);
                } catch (Exception ignored) {
                    root = null;
                }
            }
            return resolveAlarmDetails(unitId, summary, root);
        }
        return Optional.empty();
    }

    /**
     * Alarmdetails aus Listen-Payload; bei fehlenden Rückmeldungen Nachladen per {@code GET /api/v2/alarms/{id}}.
     */
    @Transactional(readOnly = true)
    public Optional<DiveraAlarmDetails> resolveAlarmDetails(long unitId, DiveraAlarmSummary summary, JsonNode root) {
        if (summary == null || summary.id() <= 0) {
            return Optional.empty();
        }
        try {
            Optional<DiveraAlarmDetails> fromList = DiveraAlarmDetailsMapper.fromSummary(summary, root);
            if (fromList.isEmpty()) {
                return Optional.empty();
            }
            DiveraAlarmDetails details = fromList.get();
            if (DiveraAlarmDetailsMapper.hasPersonnelResponses(details)) {
                return Optional.of(details);
            }
            if (testModeService.isEnabled()) {
                return Optional.of(details);
            }
            return fetchAlarmDetailsFromApi(unitId, summary.id()).or(() -> Optional.of(details));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DiveraService.class)
                    .warn("DIVERA-Alarmdetails {} konnten nicht gelesen werden: {}", summary.id(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<DiveraAlarmDetails> fetchAlarmDetailsFromApi(long unitId, long alarmId) {
        return diveraSettingsRepository
                .findByUnitId(unitId)
                .flatMap(cfg -> diveraApiClient.fetchAlarmItemById(
                        cfg.getApiBaseUrl() != null ? cfg.getApiBaseUrl() : DiveraIntegrationSupport.DEFAULT_API_BASE,
                        cfg.getAccessKey(),
                        alarmId))
                .flatMap(item -> {
                    try {
                        ObjectNode root = objectMapper.createObjectNode();
                        root.set("data", item);
                        return DiveraAlarmDetailsMapper.fromWebhookJson(root);
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                })
                .filter(DiveraAlarmDetailsMapper::hasPersonnelResponses);
    }

    private String buildTestAlarmPayload(TestDiveraAlarm alarm) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = root.putObject("data");
            data.put("id", alarm.getAlarmId());
            if (alarm.getExternalId() != null) {
                data.put("foreign_id", alarm.getExternalId());
            }
            data.put("title", alarm.getTitle() != null ? alarm.getTitle() : "");
            if (alarm.getAlarmText() != null) {
                data.put("text", alarm.getAlarmText());
            }
            if (alarm.getAddress() != null) {
                data.put("address", alarm.getAddress());
            }
            data.put("ts_create", alarm.getTsCreateSeconds());
            data.put("closed", alarm.isClosed());
            return diveraAlarmRawJson.serializeWebhookBody(root.toString());
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildManualAlarmPayload(ManualAlarm alarm) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = root.putObject("data");
            data.put("id", alarm.getAlarmId());
            if (alarm.getAlarmNumber() != null) {
                data.put("foreign_id", alarm.getAlarmNumber());
            }
            data.put("title", alarm.getTitle() != null ? alarm.getTitle() : "");
            if (alarm.getAlarmText() != null) {
                data.put("text", alarm.getAlarmText());
            }
            if (alarm.getAddress() != null) {
                data.put("address", alarm.getAddress());
            }
            if (alarm.getStreet() != null) {
                data.put("street", alarm.getStreet());
            }
            if (alarm.getHouseNumber() != null) {
                data.put("house_number", alarm.getHouseNumber());
            }
            if (alarm.getPostalCode() != null) {
                data.put("zip", alarm.getPostalCode());
            }
            if (alarm.getCity() != null) {
                data.put("city", alarm.getCity());
            }
            if (alarm.getDistrict() != null) {
                data.put("ortsteil", alarm.getDistrict());
            }
            if (alarm.getObjectName() != null) {
                data.put("object", alarm.getObjectName());
            }
            if (alarm.getReporterName() != null) {
                data.put("caller", alarm.getReporterName());
            }
            if (alarm.getReporterPhone() != null) {
                data.put("caller_phone", alarm.getReporterPhone());
            }
            data.put("ts_create", alarm.getTsCreateSeconds());
            data.put("closed", alarm.isClosed());
            return diveraAlarmRawJson.serializeWebhookBody(root.toString());
        } catch (Exception e) {
            return "{}";
        }
    }
}
