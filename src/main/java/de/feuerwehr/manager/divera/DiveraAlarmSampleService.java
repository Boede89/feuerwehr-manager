package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.web.dto.DiveraAlarmSampleListItemDto;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiveraAlarmSampleService {

    private static final DateTimeFormatter CAPTURED_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final DiveraAlarmSampleRepository repository;
    private final UnitRepository unitRepository;
    private final TestModeService testModeService;
    private final DiveraAlarmRawJson diveraAlarmRawJson;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void captureFromApiResponse(long unitId, DiveraAlarmsResponse response) {
        if (!testModeService.isEnabled() || response == null || !response.success()) {
            return;
        }
        Map<Long, String> rawById =
                response.rawJsonByAlarmId() != null ? response.rawJsonByAlarmId() : Map.of();
        for (DiveraAlarmSummary alarm : response.alarms()) {
            if (alarm.testAlarm()) {
                continue;
            }
            String payload = rawById.get(alarm.id());
            if (payload == null || payload.isBlank()) {
                continue;
            }
            upsert(unitId, alarm.id(), alarm.title(), alarm.address(), payload);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void captureFromWebhook(long unitId, String rawBody) {
        if (!testModeService.isEnabled() || rawBody == null || rawBody.isBlank()) {
            return;
        }
        try {
            var root = objectMapper.readTree(rawBody);
            Optional<DiveraAlarmJsonParser.ParsedAlarm> parsed = DiveraAlarmJsonParser.parseFirst(root);
            if (parsed.isEmpty()) {
                return;
            }
            DiveraAlarmJsonParser.ParsedAlarm p = parsed.get();
            String payload = diveraAlarmRawJson.serializeWebhookBody(rawBody);
            upsert(unitId, p.alarmId(), p.title(), p.address(), payload);
        } catch (Exception ignored) {
            // ungültiges JSON — kein Beispiel speichern
        }
    }

    @Transactional(readOnly = true)
    public List<DiveraAlarmSampleListItemDto> listForUnit(long unitId) {
        if (!testModeService.isEnabled()) {
            return List.of();
        }
        return repository.findByUnitIdOrderByCapturedAtDesc(unitId).stream()
                .map(s -> new DiveraAlarmSampleListItemDto(
                        s.getId(),
                        s.getAlarmId(),
                        s.getTitle(),
                        s.getAddress(),
                        CAPTURED_FMT.format(s.getCapturedAt())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<String> payloadForUnit(long unitId, long sampleId) {
        if (!testModeService.isEnabled()) {
            return Optional.empty();
        }
        return repository.findByIdAndUnitId(sampleId, unitId).map(DiveraAlarmSample::getWebhookPayload);
    }

    private void upsert(long unitId, long alarmId, String title, String address, String payload) {
        Unit unit = unitRepository.getReferenceById(unitId);
        DiveraAlarmSample entity = repository
                .findByUnitIdAndAlarmId(unitId, alarmId)
                .orElseGet(DiveraAlarmSample::new);
        if (entity.getId() == null) {
            entity.setUnit(unit);
            entity.setAlarmId(alarmId);
        }
        entity.setTitle(title != null && !title.isBlank() ? title.trim() : "Einsatz " + alarmId);
        entity.setAddress(blankToNull(address));
        entity.setWebhookPayload(payload);
        entity.setCapturedAt(Instant.now());
        repository.save(entity);
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
