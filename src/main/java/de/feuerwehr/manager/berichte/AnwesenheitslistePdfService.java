package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.user.User;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnwesenheitslistePdfService {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SUBMITTED_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(BERLIN);

    private final AnwesenheitslisteService anwesenheitslisteService;
    private final HtmlPdfService htmlPdfService;

    @Transactional(readOnly = true)
    public byte[] renderPdf(long unitId, long reportId) {
        AttendanceReport report = anwesenheitslisteService.requireReport(unitId, reportId);
        KraefteFahrzeugeState state = anwesenheitslisteService.buildKraefteFahrzeugeState(unitId, reportId);
        Map<String, Object> model = buildModel(report, state);
        return htmlPdfService.renderPdf("berichte/anwesenheitsliste-druck", model);
    }

    public String suggestedFilename(AttendanceReport report) {
        String date = report.getEventDate() != null
                ? report.getEventDate().format(DateTimeFormatter.ISO_DATE)
                : "ohne-datum";
        String number = report.getReportNumber() != null
                ? report.getReportNumber().replaceAll("[^a-zA-Z0-9_-]", "_")
                : String.valueOf(report.getId());
        return "Anwesenheitsliste_" + date + "_" + number + ".pdf";
    }

    private Map<String, Object> buildModel(AttendanceReport report, KraefteFahrzeugeState state) {
        Unit unit = report.getUnit();
        List<EinsatzberichtPdfService.EinsatzberichtPdfPersonRow> personnel = new ArrayList<>();
        List<EinsatzberichtPdfService.EinsatzberichtPdfVehicleRow> vehicles = new ArrayList<>();
        int totalZf = 0;
        int totalGf = 0;
        int totalM = 0;

        appendVehicleRows(state.beteiligt(), personnel, vehicles, false);
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.involvedVehicles()) {
            appendVehicleRows(vehicle, personnel, vehicles, true);
            int[] parts = parseStrength(vehicle.besatzungsstaerke());
            totalZf += parts[0];
            totalGf += parts[1];
            totalM += parts[2];
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("unitLogoBase64", unit.getLogoBase64());
        model.put("eventDate", report.getEventDate() != null ? report.getEventDate().format(DATE_FMT) : "—");
        model.put(
                "typ",
                report.getTerminCategory() != null
                        ? report.getTerminCategory().displayLabel()
                        : "Anwesenheit");
        model.put("title", nullToDash(report.getTitle()));
        model.put("address", formatAddress(report, unit));
        model.put(
                "reportNumber",
                report.getReportNumber() != null && !report.getReportNumber().isBlank()
                        ? report.getReportNumber().trim()
                        : "—");
        model.put("startTime", formatTime(report.getStartTime()));
        model.put("endTime", formatTime(report.getEndTime()));
        model.put("duration", formatDuration(report.getStartTime(), report.getEndTime()));
        model.put("bericht", nullToDash(report.getNotes()));
        model.put("instructorResponsible", nullToDash(report.getInstructorResponsible()));
        model.put("submittedInfo", formatSubmittedInfo(report));
        model.put("personnel", personnel);
        model.put("vehicles", vehicles);
        model.put(
                "totalStrength",
                vehicles.isEmpty() ? "0/0/0/0" : totalZf + "/" + totalGf + "/" + totalM + "/" + (totalZf + totalGf + totalM));
        model.put("personnelCount", personnel.size());
        return model;
    }

    private void appendVehicleRows(
            KraefteFahrzeugeState.KraefteVehicleView vehicle,
            List<EinsatzberichtPdfService.EinsatzberichtPdfPersonRow> personnel,
            List<EinsatzberichtPdfService.EinsatzberichtPdfVehicleRow> vehicles,
            boolean includeVehicleRow) {
        if (vehicle == null || vehicle.crewPersons() == null) {
            return;
        }
        String vehicleLabel = vehicle.vehicleId() == IncidentCrewSupport.BETEILIGT_VEHICLE_ID
                ? "Anw."
                : vehicle.name();
        for (KraefteFahrzeugeState.KraeftePersonView person : vehicle.crewPersons()) {
            String name = person.displayName();
            if (person.unitLabel() != null && !person.unitLabel().isBlank()) {
                name = name + " (" + person.unitLabel() + ")";
            }
            personnel.add(new EinsatzberichtPdfService.EinsatzberichtPdfPersonRow(
                    name, vehicleLabel, person.usesPa() ? "X" : ""));
        }
        if (includeVehicleRow && vehicle.vehicleId() > 0) {
            String maschinist = findRoleName(vehicle.crewPersons(), "MASCHINIST");
            String einheitsfuehrer = findRoleName(vehicle.crewPersons(), "EINHEITSFUEHRER");
            vehicles.add(new EinsatzberichtPdfService.EinsatzberichtPdfVehicleRow(
                    vehicle.name(),
                    maschinist,
                    einheitsfuehrer,
                    vehicle.besatzungsstaerke(),
                    "—"));
        }
    }

    private static String findRoleName(List<KraefteFahrzeugeState.KraeftePersonView> crew, String role) {
        return crew.stream()
                .filter(person -> role.equals(person.vehicleRole()))
                .map(KraefteFahrzeugeState.KraeftePersonView::displayName)
                .findFirst()
                .orElse("—");
    }

    private String formatSubmittedInfo(AttendanceReport report) {
        Instant when = resolveSubmittedAt(report);
        String who = resolveSubmittedByName(report);
        if (when == null) {
            return "Eingereicht von " + who;
        }
        return "Eingereicht am " + SUBMITTED_FMT.format(when) + " von " + who;
    }

    private Instant resolveSubmittedAt(AttendanceReport report) {
        if ((report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                        || report.getStatus() == IncidentReportStatus.ARCHIVIERT)
                && report.getReleasedAt() != null) {
            return report.getReleasedAt();
        }
        return report.getUpdatedAt() != null ? report.getUpdatedAt() : report.getCreatedAt();
    }

    private String resolveSubmittedByName(AttendanceReport report) {
        if ((report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                        || report.getStatus() == IncidentReportStatus.ARCHIVIERT)
                && report.getReleasedByUser() != null) {
            return formatUserName(report.getReleasedByUser());
        }
        if (report.getCreatedByName() != null && !report.getCreatedByName().isBlank()) {
            return report.getCreatedByName().trim();
        }
        return "Unbekannt";
    }

    private static String formatUserName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getUsername() != null ? user.getUsername() : "Unbekannt";
    }

    private static int[] parseStrength(String strength) {
        if (strength == null || strength.isBlank()) {
            return new int[] {0, 0, 0};
        }
        String[] parts = strength.split("/");
        if (parts.length < 3) {
            return new int[] {0, 0, 0};
        }
        try {
            return new int[] {
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException e) {
            return new int[] {0, 0, 0};
        }
    }

    private static String formatAddress(AttendanceReport report, Unit unit) {
        if (report.getLocation() != null && !report.getLocation().isBlank()) {
            return report.getLocation().trim();
        }
        List<String> parts = new ArrayList<>();
        UnitAddressSupport.UnitAddress unitAddress = UnitAddressSupport.fromUnit(unit);
        if (unitAddress.street() != null && !unitAddress.street().isBlank()) {
            String street = unitAddress.street().trim();
            if (unitAddress.houseNumber() != null && !unitAddress.houseNumber().isBlank()) {
                street += " " + unitAddress.houseNumber().trim();
            }
            parts.add(street);
        }
        if (unitAddress.postalCode() != null && !unitAddress.postalCode().isBlank()) {
            parts.add(unitAddress.postalCode().trim());
        }
        if (unitAddress.location() != null && !unitAddress.location().isBlank()) {
            parts.add(unitAddress.location().trim());
        }
        return parts.isEmpty() ? "—" : String.join(", ", parts);
    }

    private static String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FMT) : "—";
    }

    private static String formatDuration(LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            return "—";
        }
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours > 0 && rest > 0) {
            return hours + " Std " + rest + " Min";
        }
        if (hours > 0) {
            return hours + " Std";
        }
        return rest + " Min";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
