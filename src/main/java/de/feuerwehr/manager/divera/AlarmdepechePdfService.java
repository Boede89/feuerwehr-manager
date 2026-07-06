package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlarmdepechePdfService {

    private final HtmlPdfService htmlPdfService;

    public byte[] renderPdf(ManualAlarm alarm) {
        if (alarm == null) {
            throw new IllegalArgumentException("Alarm fehlt.");
        }
        Map<String, Object> model = new HashMap<>();
        String alarmNumber = valueOrDash(alarm.getAlarmNumber(), String.valueOf(alarm.getAlarmId()));
        String category = valueOrDash(alarm.getIncidentCategory(), "Einsatz");
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
        model.put("routeInfo", alarm.getRouteInfo());
        model.put("hasRoute", alarm.getRouteInfo() != null && !alarm.getRouteInfo().isBlank());
        model.put("printedAt", ManualAlarmService.formatPrintTimestamp(Instant.now()));
        return htmlPdfService.renderPdf("print/alarmdepeche", model);
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
