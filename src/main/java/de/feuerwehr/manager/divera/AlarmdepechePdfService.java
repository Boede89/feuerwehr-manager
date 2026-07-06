package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.routing.AlarmRouteService.RouteStep;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlarmdepechePdfService {

    private static final DateTimeFormatter HEADER_TS =
            DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm", Locale.GERMANY);
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final HtmlPdfService htmlPdfService;
    private final ObjectMapper objectMapper;

    public byte[] renderPdf(ManualAlarm alarm) {
        if (alarm == null) {
            throw new IllegalArgumentException("Alarm fehlt.");
        }
        Map<String, Object> model = new HashMap<>();
        Instant now = Instant.now();
        String alarmNumber = valueOrDash(alarm.getAlarmNumber(), String.valueOf(alarm.getAlarmId()));
        String category = valueOrDash(alarm.getIncidentCategory(), "Einsatz");
        model.put("metaLeft", valueOrDash(alarm.getLeitstelleName(), "Leitstelle"));
        model.put("metaRight", HEADER_TS.format(now.atZone(ZONE)) + " · 1/1");
        model.put("headerTitle", "Alarmdruck " + alarmNumber + " / " + category);
        model.put("leitstelleName", valueOrDash(alarm.getLeitstelleName(), "Leitstelle"));
        model.put("leitstelleAddress", valueOrDash(alarm.getLeitstelleAddress(), "—"));
        model.put("leitstellePhone", valueOrDash(alarm.getLeitstellePhone(), "—"));
        model.put("leitstelleEmail", valueOrDash(alarm.getLeitstelleEmail(), "—"));
        model.put("meldebild", valueOrDash(alarm.getAlarmText(), "—"));
        model.put("meldebildZusatz", alarm.getMeldebildZusatz());
        model.put("stichwort", valueOrDash(alarm.getTitle(), "—"));
        model.put("reporterLine", buildReporterLine(alarm));
        model.put("callbackPhone", valueOrDash(alarm.getCallbackPhone(), "/"));
        model.put("meldeweg", valueOrDash(alarm.getMeldeweg(), "—"));
        model.put("objectName", valueOrDash(alarm.getObjectName(), "—"));
        model.put("cityLine", buildCityLine(alarm));
        model.put("district", valueOrDash(alarm.getDistrict(), "—"));
        model.put("streetLine", buildStreetLine(alarm));
        model.put("beteiligteEinsatzmittel", valueOrDash(alarm.getBeteiligteEinsatzmittel(), "—"));

        List<RouteStep> steps = parseSteps(alarm.getRouteStepsJson());
        model.put("routeSteps", steps);
        model.put("hasRoute", !steps.isEmpty() || (alarm.getRouteInfo() != null && !alarm.getRouteInfo().isBlank()));
        model.put("routeTitle", valueOrDash(alarm.getRouteTitle(), "Einsatzstelle"));
        model.put("routeSummary", buildRouteSummary(alarm));
        model.put("routeInfo", alarm.getRouteInfo());
        model.put("printedAt", ManualAlarmService.formatPrintTimestamp(now));
        return htmlPdfService.renderPdf("print/alarmdepeche", model);
    }

    private List<RouteStep> parseSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RouteStep>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String buildRouteSummary(ManualAlarm alarm) {
        if (alarm.getRouteDistanceM() == null || alarm.getRouteDurationSec() == null) {
            return null;
        }
        int minutes = Math.max(1, (int) Math.round(alarm.getRouteDurationSec() / 60.0));
        double speed = alarm.getRouteAvgSpeedKmh() != null ? alarm.getRouteAvgSpeedKmh() : 0;
        return alarm.getRouteDistanceM()
                + " m ("
                + minutes
                + (minutes == 1 ? " Minute" : " Minuten")
                + ") bei durchschnittlich "
                + String.format(Locale.GERMANY, "%.1f", speed)
                + " km/h";
    }

    private static String buildReporterLine(ManualAlarm alarm) {
        String name = alarm.getReporterName();
        String phone = alarm.getReporterPhone();
        if (name != null && !name.isBlank() && phone != null && !phone.isBlank()) {
            return name.trim() + " / " + phone.trim();
        }
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (phone != null && !phone.isBlank()) {
            return phone.trim();
        }
        return "—";
    }

    private static String buildCityLine(ManualAlarm alarm) {
        String city = alarm.getCity();
        String plz = alarm.getPostalCode();
        if (city != null && !city.isBlank() && plz != null && !plz.isBlank()) {
            return city.trim() + " [" + plz.trim() + "]";
        }
        if (city != null && !city.isBlank()) {
            return city.trim();
        }
        if (plz != null && !plz.isBlank()) {
            return "[" + plz.trim() + "]";
        }
        return "—";
    }

    private static String buildStreetLine(ManualAlarm alarm) {
        String street = alarm.getStreet();
        String house = alarm.getHouseNumber();
        if (street != null && !street.isBlank() && house != null && !house.isBlank()) {
            return street.trim() + " " + house.trim();
        }
        if (street != null && !street.isBlank()) {
            return street.trim();
        }
        return "—";
    }

    private static String valueOrDash(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return fallback;
    }
}
