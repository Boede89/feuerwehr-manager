package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.berichte.UnitAddressSupport;
import de.feuerwehr.manager.divera.DiveraAlarmDetailsMapper.DiveraAlarmDetails;
import de.feuerwehr.manager.einsatzapp.EinsatzAppPushService;
import de.feuerwehr.manager.print.CupsPrintService;
import de.feuerwehr.manager.print.UnitPrintSettingsService;
import de.feuerwehr.manager.routing.AlarmRouteService;
import de.feuerwehr.manager.routing.AlarmRouteService.RoutePlan;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualAlarmService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter PRINT_TS =
            DateTimeFormatter.ofPattern("d.MMMM yyyy H:mm", Locale.GERMANY);
    private static final long MANUAL_ALARM_ID_BASE = 7_000_000_000L;

    private final ManualAlarmRepository repository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final EinsatzAppPushService einsatzAppPushService;
    private final AlarmdepechePdfService alarmdepechePdfService;
    private final UnitPrintSettingsService unitPrintSettingsService;
    private final AlarmRouteService alarmRouteService;
    private final ObjectMapper objectMapper;

    public record ManualAlarmInput(
            String alarmNumber,
            String incidentCategory,
            String title,
            String alarmText,
            String meldebildZusatz,
            String street,
            String houseNumber,
            String postalCode,
            String city,
            String district,
            String objectName,
            String reporterName,
            String reporterPhone,
            String callbackPhone,
            String meldeweg,
            String beteiligteEinsatzmittel,
            String leitstelleName,
            String leitstelleAddress,
            String leitstellePhone,
            String leitstelleEmail) {}

    public record CreateResult(ManualAlarm alarm, String pushMessage, String printMessage, String routeMessage) {}

    @Transactional
    public CreateResult create(
            long unitId,
            long userId,
            ManualAlarmInput input,
            boolean useGeraetehaus,
            String routeStartAddressOverride,
            boolean computeRoute,
            boolean sendPush,
            boolean printDepesche) {
        if (input == null || input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException("Stichwort ist erforderlich.");
        }
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        User user = userRepository.findById(userId).orElse(null);
        long now = Instant.now().getEpochSecond();
        ManualAlarm alarm = new ManualAlarm();
        alarm.setUnit(unit);
        alarm.setAlarmId(generateAlarmId());
        alarm.setAlarmNumber(blankToNull(input.alarmNumber()));
        alarm.setIncidentCategory(blankToNull(input.incidentCategory()));
        alarm.setTitle(input.title().trim());
        alarm.setAlarmText(blankToNull(input.alarmText()));
        alarm.setMeldebildZusatz(blankToNull(input.meldebildZusatz()));
        alarm.setStreet(blankToNull(input.street()));
        alarm.setHouseNumber(blankToNull(input.houseNumber()));
        alarm.setPostalCode(blankToNull(input.postalCode()));
        alarm.setCity(blankToNull(input.city()));
        alarm.setDistrict(blankToNull(input.district()));
        alarm.setObjectName(blankToNull(input.objectName()));
        alarm.setReporterName(blankToNull(input.reporterName()));
        alarm.setReporterPhone(blankToNull(input.reporterPhone()));
        alarm.setCallbackPhone(blankToNull(input.callbackPhone()));
        alarm.setMeldeweg(blankToNull(input.meldeweg()));
        alarm.setBeteiligteEinsatzmittel(blankToNull(input.beteiligteEinsatzmittel()));
        alarm.setLeitstelleName(blankToNull(input.leitstelleName()));
        alarm.setLeitstelleAddress(blankToNull(input.leitstelleAddress()));
        alarm.setLeitstellePhone(blankToNull(input.leitstellePhone()));
        alarm.setLeitstelleEmail(blankToNull(input.leitstelleEmail()));
        alarm.setAddress(buildAddressLine(alarm));
        alarm.setDateEpochSeconds(now);
        alarm.setTsCreateSeconds(now);
        alarm.setClosed(false);
        alarm.setCreatedAt(Instant.now());
        alarm.setCreatedBy(user);

        String routeMessage = applyRoute(alarm, unit, useGeraetehaus, routeStartAddressOverride, computeRoute);
        ManualAlarm saved = repository.save(alarm);

        String pushMessage = null;
        if (sendPush) {
            einsatzAppPushService.dispatchManualAlarm(unitId, toDetails(saved));
            pushMessage = einsatzAppPushService.describeLastPush(unitId, saved.getAlarmId());
        }
        String printMessage = null;
        if (printDepesche) {
            CupsPrintService.CupsPrintResult printResult =
                    unitPrintSettingsService.printPdf(unitId, alarmdepechePdfService.renderPdf(saved));
            printMessage = printResult.message();
        }
        return new CreateResult(saved, pushMessage, printMessage, routeMessage);
    }

    private String applyRoute(
            ManualAlarm alarm, Unit unit, boolean useGeraetehaus, String routeStartOverride, boolean computeRoute) {
        if (!computeRoute) {
            return null;
        }
        String start = blankToNull(routeStartOverride);
        if (start == null && useGeraetehaus) {
            start = blankToNull(UnitAddressSupport.fullAddressLine(unit));
        }
        if (start == null) {
            return "Route: Keine Startadresse (Gerätehaus-Adresse in Einheit-Stammdaten pflegen).";
        }
        String destination = buildAddressLine(alarm);
        if (destination == null) {
            return "Route: Einsatzort unvollständig — keine Route berechnet.";
        }
        alarm.setRouteStartAddress(start);
        Optional<RoutePlan> plan = alarmRouteService.planRoute(start, destination, resolveRouteTitle(alarm, unit));
        if (plan.isEmpty()) {
            return "Route: Automatische Berechnung fehlgeschlagen (Adresse prüfen).";
        }
        RoutePlan route = plan.get();
        alarm.setRouteDistanceM(route.distanceMeters());
        alarm.setRouteDurationSec(route.durationSeconds());
        alarm.setRouteAvgSpeedKmh(
                BigDecimal.valueOf(route.avgSpeedKmh()).setScale(1, RoundingMode.HALF_UP));
        alarm.setRouteTitle(route.routeTitle());
        alarm.setRouteInfo(route.plainText());
        try {
            alarm.setRouteStepsJson(objectMapper.writeValueAsString(route.steps()));
        } catch (JsonProcessingException e) {
            log.warn("Route-JSON konnte nicht gespeichert werden: {}", e.getMessage());
        }
        return "Route berechnet: " + route.distanceMeters() + " m, ca. "
                + Math.max(1, Math.round(route.durationSeconds() / 60.0)) + " Min.";
    }

    private static String resolveRouteTitle(ManualAlarm alarm, Unit unit) {
        String beteiligte = alarm.getBeteiligteEinsatzmittel();
        if (beteiligte != null && !beteiligte.isBlank()) {
            return beteiligte.replace("|", "").trim();
        }
        if (unit.getName() != null && !unit.getName().isBlank()) {
            return unit.getName().trim();
        }
        return "Einsatzstelle";
    }

    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> listOpenSummariesForUnit(long unitId) {
        return repository.findByUnitIdAndClosedFalseOrderByCreatedAtDesc(unitId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void closeAlarm(long unitId, long manualRecordId) {
        ManualAlarm alarm = repository
                .findByIdAndUnitId(manualRecordId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Manueller Einsatz nicht gefunden."));
        if (alarm.isClosed()) {
            return;
        }
        alarm.setClosed(true);
        alarm.setClosedAt(Instant.now());
        repository.save(alarm);
    }

    @Transactional(readOnly = true)
    public Optional<DiveraAlarmDetails> findDetails(long unitId, long alarmId) {
        return repository.findByUnitIdAndAlarmId(unitId, alarmId).map(this::toDetails);
    }

    public List<DiveraAlarmSummary> mergeInto(List<DiveraAlarmSummary> base, List<DiveraAlarmSummary> manualAlarms) {
        if (manualAlarms == null || manualAlarms.isEmpty()) {
            return base != null ? base : List.of();
        }
        List<DiveraAlarmSummary> merged = new ArrayList<>();
        if (base != null) {
            merged.addAll(base);
        }
        merged.addAll(manualAlarms);
        return merged.stream()
                .sorted(Comparator.comparingLong(DiveraAlarmSummary::dateEpochSeconds).reversed())
                .toList();
    }

    private DiveraAlarmSummary toSummary(ManualAlarm a) {
        return DiveraAlarmSummary.fromManualAlarm(
                a.getAlarmId(),
                a.getId(),
                a.getTitle(),
                a.getAlarmText(),
                a.getAddress(),
                a.getDateEpochSeconds(),
                a.getTsCreateSeconds(),
                a.isClosed());
    }

    private DiveraAlarmDetails toDetails(ManualAlarm alarm) {
        return new DiveraAlarmDetails(
                alarm.getAlarmId(),
                alarm.getAlarmNumber(),
                alarm.getTitle(),
                alarm.getAlarmText(),
                alarm.getAddress(),
                alarm.getDateEpochSeconds(),
                alarm.getTsCreateSeconds(),
                0L,
                0L,
                0L,
                alarm.isClosed(),
                alarm.getPostalCode(),
                alarm.getStreet(),
                alarm.getHouseNumber(),
                alarm.getCity(),
                alarm.getDistrict(),
                alarm.getObjectName(),
                null,
                alarm.getReporterName(),
                alarm.getReporterPhone(),
                null,
                null,
                null,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private long generateAlarmId() {
        return MANUAL_ALARM_ID_BASE + (System.currentTimeMillis() % 999_999_999L);
    }

    static String buildAddressLine(ManualAlarm alarm) {
        StringBuilder sb = new StringBuilder();
        if (alarm.getStreet() != null && !alarm.getStreet().isBlank()) {
            sb.append(alarm.getStreet().trim());
            if (alarm.getHouseNumber() != null && !alarm.getHouseNumber().isBlank()) {
                sb.append(' ').append(alarm.getHouseNumber().trim());
            }
        }
        if (alarm.getPostalCode() != null && !alarm.getPostalCode().isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(alarm.getPostalCode().trim());
        }
        if (alarm.getCity() != null && !alarm.getCity().isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(alarm.getCity().trim());
        }
        if (alarm.getDistrict() != null && !alarm.getDistrict().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" (").append(alarm.getDistrict().trim()).append(')');
            } else {
                sb.append(alarm.getDistrict().trim());
            }
        }
        String line = sb.toString().trim();
        return line.isEmpty() ? null : line;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String formatPrintTimestamp(Instant instant) {
        if (instant == null) {
            instant = Instant.now();
        }
        return PRINT_TS.format(instant.atZone(ZONE));
    }
}
