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
        String alarmNumber = hasText(alarm.getAlarmNumber()) ? alarm.getAlarmNumber().trim() : String.valueOf(alarm.getAlarmId());
        String category = hasText(alarm.getIncidentCategory()) ? alarm.getIncidentCategory().trim() : "Einsatz";

        putIfPresent(model, "metaLeft", alarm.getLeitstelleName());
        model.put("metaRight", HEADER_TS.format(now.atZone(ZONE)) + " · 1/1");
        model.put("headerTitle", "Alarmdruck " + alarmNumber + " / " + category);

        putIfPresent(model, "leitstelleName", alarm.getLeitstelleName());
        putIfPresent(model, "leitstelleAddress", alarm.getLeitstelleAddress());
        putIfPresent(model, "leitstellePhone", alarm.getLeitstellePhone());
        putIfPresent(model, "leitstelleEmail", alarm.getLeitstelleEmail());
        model.put("hasLeitstelle", model.containsKey("leitstelleName")
                || model.containsKey("leitstelleAddress")
                || model.containsKey("leitstellePhone")
                || model.containsKey("leitstelleEmail"));

        putIfPresent(model, "meldebild", alarm.getAlarmText());
        putIfPresent(model, "bemerkung", alarm.getMeldebildZusatz());
        model.put("sondersignalText", alarm.isSondersignal() ? "Mit Sondersignal" : "ohne Sondersignal");

        putIfPresent(model, "stichwort", alarm.getTitle());
        putIfPresent(model, "reporterLine", buildReporterLine(alarm));
        putIfPresent(model, "meldeweg", alarm.getMeldeweg());
        putIfPresent(model, "objectName", alarm.getObjectName());
        putIfPresent(model, "cityLine", buildCityLine(alarm));
        putIfPresent(model, "district", alarm.getDistrict());
        putIfPresent(model, "streetLine", buildStreetLine(alarm));
        putIfPresent(model, "beteiligteEinsatzmittel", alarm.getBeteiligteEinsatzmittel());

        List<RouteStep> steps = parseSteps(alarm.getRouteStepsJson());
        model.put("routeSteps", steps);
        model.put("hasRoute", !steps.isEmpty() || hasText(alarm.getRouteInfo()));
        putIfPresent(model, "routeTitle", alarm.getRouteTitle());
        putIfPresent(model, "routeSummary", buildRouteSummary(alarm));
        putIfPresent(model, "routeInfo", alarm.getRouteInfo());
        model.put("printedAt", ManualAlarmService.formatPrintTimestamp(now));
        return htmlPdfService.renderPdf("print/alarmdepeche", model);
    }

    private List<RouteStep> parseSteps(String json) {
        if (!hasText(json)) {
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
        double speed = alarm.getRouteAvgSpeedKmh() != null ? alarm.getRouteAvgSpeedKmh().doubleValue() : 0;
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
        if (hasText(name) && hasText(phone)) {
            return name.trim() + " / " + phone.trim();
        }
        if (hasText(name)) {
            return name.trim();
        }
        if (hasText(phone)) {
            return phone.trim();
        }
        return null;
    }

    private static String buildCityLine(ManualAlarm alarm) {
        String city = alarm.getCity();
        String plz = alarm.getPostalCode();
        if (hasText(city) && hasText(plz)) {
            return city.trim() + " [" + plz.trim() + "]";
        }
        if (hasText(city)) {
            return city.trim();
        }
        if (hasText(plz)) {
            return "[" + plz.trim() + "]";
        }
        return null;
    }

    private static String buildStreetLine(ManualAlarm alarm) {
        String street = alarm.getStreet();
        String house = alarm.getHouseNumber();
        if (hasText(street) && hasText(house)) {
            return street.trim() + " " + house.trim();
        }
        if (hasText(street)) {
            return street.trim();
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> model, String key, String value) {
        if (hasText(value)) {
            model.put(key, value.trim());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
