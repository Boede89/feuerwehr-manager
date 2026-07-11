package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.berichte.IncidentNumberSupport;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
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
import java.time.LocalDate;
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
    private static final long MANUAL_ALARM_ID_BASE = 1_000_000_000L;
    private static final long MANUAL_ALARM_ID_SPAN = 999_000_000L;

    private final ManualAlarmRepository repository;
    private final IncidentReportRepository incidentReportRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final EinsatzAppPushService einsatzAppPushService;
    private final AlarmdepechePdfService alarmdepechePdfService;
    private final UnitPrintSettingsService unitPrintSettingsService;
    private final AlarmRouteService alarmRouteService;
    private final ObjectMapper objectMapper;

    public record ManualAlarmInput(
            String alarmNumber,
            String title,
            String meldebild,
            String bemerkung,
            String street,
            String houseNumber,
            String postalCode,
            String city,
            String district,
            String objectName,
            String reporterName,
            String reporterPhone,
            String meldeweg,
            String beteiligteEinsatzmittel,
            String leitstelleName,
            String leitstelleAddress,
            String leitstellePhone,
            String leitstelleEmail,
            boolean exercise,
            boolean sondersignal,
            boolean routePlanUseGeraetehaus,
            String routePlanStartAddress) {}

    public record CreateResult(ManualAlarm alarm) {}

    public record UpdateResult(ManualAlarm alarm) {}

    public record StartResult(String pushMessage, String printMessage, String routeMessage) {}

    public record ActionResult(String message) {}

    @Transactional(readOnly = true)
    public String suggestAlarmNumber(long unitId) {
        LocalDate today = LocalDate.now(ZONE);
        String yearPrefix = today.getYear() + "-";
        List<String> existing = new ArrayList<>();
        existing.addAll(incidentReportRepository.findIncidentNumbersForYear(unitId, yearPrefix, false));
        existing.addAll(repository.findAlarmNumbersForYear(unitId, yearPrefix));
        return IncidentNumberSupport.suggestForDate(today, existing);
    }

    @Transactional
    public CreateResult createDraft(long unitId, long userId, ManualAlarmInput input) {
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        User user = userRepository.findById(userId).orElse(null);
        long now = Instant.now().getEpochSecond();
        ManualAlarm alarm = new ManualAlarm();
        alarm.setUnit(unit);
        alarm.setAlarmId(generateAlarmId());
        applyInput(alarm, input, true, unitId);
        alarm.setDateEpochSeconds(now);
        alarm.setTsCreateSeconds(now);
        alarm.setStarted(false);
        alarm.setClosed(false);
        alarm.setCreatedAt(Instant.now());
        alarm.setCreatedBy(user);
        return new CreateResult(repository.save(alarm));
    }

    @Transactional(readOnly = true)
    public ManualAlarm getOpenDraft(long unitId, long manualRecordId) {
        return loadOpenDraft(unitId, manualRecordId, true);
    }

    @Transactional
    public UpdateResult updateDraft(long unitId, long manualRecordId, ManualAlarmInput input) {
        ManualAlarm alarm = loadOpenDraft(unitId, manualRecordId);
        applyInput(alarm, input, false, unitId);
        clearRouteData(alarm);
        return new UpdateResult(repository.save(alarm));
    }

    @Transactional
    public StartResult startAlarm(
            long unitId, long manualRecordId, boolean computeRoute, boolean sendPush, boolean printDepesche) {
        ManualAlarm alarm = loadOpenDraft(unitId, manualRecordId, true);
        Unit unit = alarm.getUnit();
        long now = Instant.now().getEpochSecond();
        alarm.setStarted(true);
        alarm.setStartedAt(Instant.now());
        alarm.setDateEpochSeconds(now);
        alarm.setTsCreateSeconds(now);
        ManualAlarm saved = repository.save(alarm);

        String pushMessage = null;
        if (sendPush) {
            einsatzAppPushService.dispatchManualAlarm(unitId, toDetails(saved));
            pushMessage = einsatzAppPushService.describeLastPush(unitId, saved.getAlarmId());
        }

        String routeMessage = null;
        if (computeRoute) {
            RouteStartPlan plan = resolveRouteStartPlan(saved, unit);
            routeMessage = applyRoute(saved, unit, plan.useGeraetehaus(), plan.startAddressOverride(), true);
            saved = repository.save(saved);
        }
        String printMessage = null;
        if (printDepesche) {
            printMessage = printDepescheInternal(unitId, saved).message();
        }
        return new StartResult(pushMessage, printMessage, routeMessage);
    }

    public record DepeschePdfResult(ManualAlarm alarm, byte[] pdf) {}

    @Transactional
    public DepeschePdfResult buildDepeschePdf(long unitId, long manualRecordId, boolean computeRoute) {
        ManualAlarm alarm = prepareAlarmForDepesche(unitId, manualRecordId, computeRoute);
        return new DepeschePdfResult(alarm, alarmdepechePdfService.renderPdf(alarm));
    }

    @Transactional
    public ActionResult printDepesche(long unitId, long manualRecordId, boolean computeRoute) {
        ManualAlarm alarm = prepareAlarmForDepesche(unitId, manualRecordId, computeRoute);
        CupsPrintService.CupsPrintResult printResult = printDepescheInternal(unitId, alarm);
        return new ActionResult(printResult.message());
    }

    private ManualAlarm prepareAlarmForDepesche(long unitId, long manualRecordId, boolean computeRoute) {
        ManualAlarm alarm = repository
                .findByIdAndUnitIdWithUnit(manualRecordId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einsatz nicht gefunden."));
        if (alarm.isClosed()) {
            throw new IllegalArgumentException("Beendete Einsätze können nicht mehr als Depesche ausgegeben werden.");
        }
        if (computeRoute) {
            RouteStartPlan plan = resolveRouteStartPlan(alarm, alarm.getUnit());
            applyRoute(alarm, alarm.getUnit(), plan.useGeraetehaus(), plan.startAddressOverride(), true);
            alarm = repository.save(alarm);
        }
        return alarm;
    }

    @Transactional
    public void deleteDraft(long unitId, long manualRecordId) {
        ManualAlarm alarm = loadOpenDraft(unitId, manualRecordId);
        repository.delete(alarm);
    }

    private CupsPrintService.CupsPrintResult printDepescheInternal(long unitId, ManualAlarm alarm) {
        return unitPrintSettingsService.printPdf(unitId, alarmdepechePdfService.renderPdf(alarm));
    }

    private ManualAlarm loadOpenDraft(long unitId, long manualRecordId) {
        return loadOpenDraft(unitId, manualRecordId, false);
    }

    private ManualAlarm loadOpenDraft(long unitId, long manualRecordId, boolean fetchUnit) {
        ManualAlarm alarm = (fetchUnit
                        ? repository.findByIdAndUnitIdWithUnit(manualRecordId, unitId)
                        : repository.findByIdAndUnitId(manualRecordId, unitId))
                .orElseThrow(() -> new IllegalArgumentException("Einsatz nicht gefunden."));
        if (alarm.isClosed()) {
            throw new IllegalArgumentException("Einsatz ist bereits beendet.");
        }
        if (alarm.isStarted()) {
            throw new IllegalArgumentException("Einsatz wurde bereits gestartet.");
        }
        return alarm;
    }

    private void applyInput(ManualAlarm alarm, ManualAlarmInput input, boolean isNew, long unitId) {
        if (input == null || input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException("Stichwort ist erforderlich.");
        }
        String alarmNumber = blankToNull(input.alarmNumber());
        if (alarmNumber != null) {
            alarm.setAlarmNumber(alarmNumber);
        } else if (isNew) {
            alarm.setAlarmNumber(suggestAlarmNumber(unitId));
        }
        alarm.setIncidentCategory(deriveIncidentCategory(input.title(), input.exercise()));
        alarm.setTitle(input.title().trim());
        alarm.setAlarmText(blankToNull(input.meldebild()));
        alarm.setMeldebildZusatz(blankToNull(input.bemerkung()));
        alarm.setStreet(blankToNull(input.street()));
        alarm.setHouseNumber(blankToNull(input.houseNumber()));
        alarm.setPostalCode(blankToNull(input.postalCode()));
        alarm.setCity(blankToNull(input.city()));
        alarm.setDistrict(blankToNull(input.district()));
        alarm.setObjectName(blankToNull(input.objectName()));
        alarm.setReporterName(blankToNull(input.reporterName()));
        alarm.setReporterPhone(blankToNull(input.reporterPhone()));
        alarm.setMeldeweg(blankToNull(input.meldeweg()));
        alarm.setBeteiligteEinsatzmittel(blankToNull(input.beteiligteEinsatzmittel()));
        alarm.setLeitstelleName(blankToNull(input.leitstelleName()));
        alarm.setLeitstelleAddress(blankToNull(input.leitstelleAddress()));
        alarm.setLeitstellePhone(blankToNull(input.leitstellePhone()));
        alarm.setLeitstelleEmail(blankToNull(input.leitstelleEmail()));
        alarm.setExercise(input.exercise());
        alarm.setSondersignal(input.sondersignal());
        alarm.setRoutePlanUseGeraetehaus(input.routePlanUseGeraetehaus());
        if (input.routePlanUseGeraetehaus()) {
            alarm.setRoutePlanStartAddress(null);
        } else {
            String planStart = blankToNull(input.routePlanStartAddress());
            if (planStart == null) {
                throw new IllegalArgumentException(
                        "Startadresse erforderlich, wenn Gerätehaus nicht als Routenstart verwendet wird.");
            }
            alarm.setRoutePlanStartAddress(planStart);
        }
        alarm.setAddress(buildAddressLine(alarm));
    }

    private record RouteStartPlan(boolean useGeraetehaus, String startAddressOverride) {}

    private static RouteStartPlan resolveRouteStartPlan(ManualAlarm alarm, Unit unit) {
        if (alarm.isRoutePlanUseGeraetehaus()) {
            return new RouteStartPlan(true, null);
        }
        return new RouteStartPlan(false, alarm.getRoutePlanStartAddress());
    }

    private static void clearRouteData(ManualAlarm alarm) {
        alarm.setRouteInfo(null);
        alarm.setRouteStartAddress(null);
        alarm.setRouteDistanceM(null);
        alarm.setRouteDurationSec(null);
        alarm.setRouteAvgSpeedKmh(null);
        alarm.setRouteStepsJson(null);
        alarm.setRouteTitle(null);
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
        String destination = buildRoutingDestination(alarm);
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

    private static String deriveIncidentCategory(String title, boolean exercise) {
        if (exercise) {
            return "Übung";
        }
        if (title == null || title.isBlank()) {
            return "Einsatz";
        }
        String trimmed = title.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == 'F' && Character.isDigit(trimmed.charAt(1))) {
            int space = trimmed.indexOf(' ');
            String prefix = space > 0 ? trimmed.substring(0, space) : trimmed;
            if (prefix.matches("F\\d+")) {
                return "Feuer " + prefix.substring(1);
            }
        }
        return "Einsatz";
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
    public List<DiveraAlarmSummary> listDraftSummariesForUnit(long unitId) {
        return repository.findByUnitIdAndStartedFalseAndClosedFalseOrderByCreatedAtDesc(unitId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DiveraAlarmSummary> listActiveSummariesForUnit(long unitId) {
        return repository.findByUnitIdAndStartedTrueAndClosedFalseOrderByStartedAtDesc(unitId).stream()
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
        long displayEpoch = a.isStarted() && a.getStartedAt() != null
                ? a.getStartedAt().getEpochSecond()
                : a.getDateEpochSeconds();
        return DiveraAlarmSummary.fromManualAlarm(
                a.getAlarmId(),
                a.getId(),
                a.getTitle(),
                a.getAlarmText(),
                a.getAddress(),
                displayEpoch,
                a.getTsCreateSeconds(),
                a.isClosed(),
                a.isStarted(),
                a.isExercise());
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
        return MANUAL_ALARM_ID_BASE + (System.currentTimeMillis() % MANUAL_ALARM_ID_SPAN);
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

    /** Zieladresse für Routing — auch ohne Straße/Hausnummer (PLZ/Ort/Ortsteil/Objekt). */
    static String buildRoutingDestination(ManualAlarm alarm) {
        String line = buildAddressLine(alarm);
        if (alarm.getObjectName() != null && !alarm.getObjectName().isBlank()) {
            String object = alarm.getObjectName().trim();
            if (line != null && !line.isBlank()) {
                return object + ", " + line;
            }
            return object;
        }
        return line;
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
