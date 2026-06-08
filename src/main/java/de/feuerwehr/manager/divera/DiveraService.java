package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public DiveraAlarmsResponse getAlarmsForUnit(long unitId) {
        if (testModeService.isEnabled()) {
            List<DiveraAlarmSummary> running = testDiveraAlarmService.listOpenSummariesForUnit(unitId);
            return DiveraAlarmsResponse.ok(running);
        }

        DiveraAlarmsResponse apiResponse = diveraSettingsRepository
                .findByUnitId(unitId)
                .map(cfg -> diveraApiClient.fetchAlarms(cfg.getApiBaseUrl(), cfg.getAccessKey()))
                .orElse(DiveraAlarmsResponse.fail("Keine Divera-Einstellungen für diese Einheit"));

        return withoutClosedAlarms(apiResponse);
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
        return DiveraAlarmsResponse.ok(alarms, rawJsonByAlarmId);
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
}
