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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Set<Long> seenPersonIds = new LinkedHashSet<>();
        appendVehicleRows(state.beteiligt(), personnel, vehicles, false, seenPersonIds);
        List<KraefteFahrzeugeState.KraefteVehicleView> allVehicles =
                state.vehicles() != null ? state.vehicles() : List.of();
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : allVehicles) {
            appendVehicleRows(vehicle, personnel, vehicles, vehicle.involvedInIncident(), seenPersonIds);
            if (vehicle.involvedInIncident()) {
                int[] parts = parseStrength(vehicle.besatzungsstaerke());
                totalZf += parts[0];
                totalGf += parts[1];
                totalM += parts[2];
            }
        }
        appendVehicleRows(state.einsatzstelle(), personnel, vehicles, false, seenPersonIds);
        appendVehicleRows(state.wache(), personnel, vehicles, false, seenPersonIds);

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
            boolean includeVehicleRow,
            Set<Long> seenPersonIds) {
        if (vehicle == null) {
            return;
        }
        String vehicleLabel = vehicle.vehicleId() == IncidentCrewSupport.BETEILIGT_VEHICLE_ID
                ? "—"
                : vehicle.name();
        if (vehicle.crewPersons() != null) {
            for (KraefteFahrzeugeState.KraeftePersonView person : vehicle.crewPersons()) {
                if (seenPersonIds != null && !seenPersonIds.add(person.id())) {
                    continue;
                }
                String name = person.displayName();
                if (person.unitLabel() != null && !person.unitLabel().isBlank()) {
                    name = name + " (" + person.unitLabel() + ")";
                }
                personnel.add(new EinsatzberichtPdfService.EinsatzberichtPdfPersonRow(
                        name, vehicleLabel, paCsaMark(person)));
            }
        }
        if (includeVehicleRow && vehicle.vehicleId() > 0) {
            List<KraefteFahrzeugeState.KraeftePersonView> crew =
                    vehicle.crewPersons() != null ? vehicle.crewPersons() : List.of();
            String maschinist = findRoleName(crew, "MASCHINIST");
            String einheitsfuehrer = findRoleName(crew, "EINHEITSFUEHRER");
            vehicles.add(new EinsatzberichtPdfService.EinsatzberichtPdfVehicleRow(
                    vehicle.name(),
                    maschinist,
                    einheitsfuehrer,
                    vehicle.besatzungsstaerke(),
                    "—"));
        }
    }

    private static String paCsaMark(KraefteFahrzeugeState.KraeftePersonView person) {
        boolean pa = person.usesPa();
        boolean csa = person.usesCsa();
        if (pa && csa) {
            return "X/C";
        }
        if (pa) {
            return "X";
        }
        if (csa) {
            return "C";
        }
        return "";
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
        List<String> parts = new ArrayList<>();
        if (report.getStreet() != null && !report.getStreet().isBlank()) {
            String street = report.getStreet().trim();
            if (report.getHouseNumber() != null && !report.getHouseNumber().isBlank()) {
                street += " " + report.getHouseNumber().trim();
            }
            parts.add(street);
        }
        if (report.getPostalCode() != null && !report.getPostalCode().isBlank()) {
            parts.add(report.getPostalCode().trim());
        }
        if (report.getLocation() != null && !report.getLocation().isBlank()) {
            parts.add(report.getLocation().trim());
        }
        if (parts.isEmpty() && report.getObjekt() != null && !report.getObjekt().isBlank()) {
            return report.getObjekt().trim();
        }
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
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
