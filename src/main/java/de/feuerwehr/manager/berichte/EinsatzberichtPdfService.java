package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.pdf.HtmlPdfService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.user.User;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EinsatzberichtPdfService {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SUBMITTED_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(BERLIN);

    private final EinsatzberichtService einsatzberichtService;
    private final HtmlPdfService htmlPdfService;
    private final IncidentReportEquipmentRepository incidentReportEquipmentRepository;
    private final IncidentReportChangeRepository incidentReportChangeRepository;

    @Transactional(readOnly = true)
    public byte[] renderPdf(long unitId, long reportId) {
        IncidentReport report = einsatzberichtService.requireReport(unitId, reportId);
        KraefteFahrzeugeState state = einsatzberichtService.buildKraefteFahrzeugeState(unitId, reportId);
        Map<String, Object> model = buildModel(report, state, reportId);
        return htmlPdfService.renderPdf("berichte/einsatzbericht-druck", model);
    }

    public String suggestedFilename(IncidentReport report) {
        String date = report.getIncidentDate() != null
                ? report.getIncidentDate().format(DateTimeFormatter.ISO_DATE)
                : "ohne-datum";
        String number = report.getIncidentNumber() != null
                ? report.getIncidentNumber().replaceAll("[^a-zA-Z0-9_-]", "_")
                : String.valueOf(report.getId());
        return "Einsatzbericht_" + date + "_" + number + ".pdf";
    }

    private Map<String, Object> buildModel(IncidentReport report, KraefteFahrzeugeState state, long reportId) {
        Unit unit = report.getUnit();
        Map<Long, String> equipmentByVehicleId = loadEquipmentNames(reportId);
        List<EinsatzberichtPdfPersonRow> personnel = new ArrayList<>();
        List<EinsatzberichtPdfVehicleRow> vehicles = new ArrayList<>();
        int totalZf = 0;
        int totalGf = 0;
        int totalM = 0;

        appendVehicleRows(state.beteiligt(), equipmentByVehicleId, personnel, vehicles, false);
        for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.involvedVehicles()) {
            appendVehicleRows(vehicle, equipmentByVehicleId, personnel, vehicles, true);
            int[] parts = parseStrength(vehicle.besatzungsstaerke());
            totalZf += parts[0];
            totalGf += parts[1];
            totalM += parts[2];
        }
        appendVehicleRows(state.einsatzstelle(), equipmentByVehicleId, personnel, vehicles, false);
        appendVehicleRows(state.wache(), equipmentByVehicleId, personnel, vehicles, false);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("unitLogoBase64", unit.getLogoBase64());
        model.put("incidentDate", report.getIncidentDate() != null ? report.getIncidentDate().format(DATE_FMT) : "—");
        model.put("stichwort", nullToDash(report.getStichwort()));
        model.put("address", formatAddress(report));
        model.put("alarmierungDurch", nullToDash(report.getAlarmierungDurch()));
        model.put(
                "incidentNumber",
                report.getIncidentNumber() != null && !report.getIncidentNumber().isBlank()
                        ? report.getIncidentNumber().trim()
                        : "—");
        model.put("alarmTime", formatTime(report.getAlarmTime()));
        model.put("endTime", formatTime(report.getEndTime()));
        model.put("duration", formatDuration(report.getAlarmTime(), report.getEndTime()));
        model.put("eigentuemer", nullToDash(report.getEigentuemer()));
        model.put("chargeable", formatJaNein(report.getChargeable()));
        model.put("fireWatch", formatJaNein(report.getFireWatch()));
        model.put("personDamages", formatPersonDamages(report));
        model.put("einsatzkurzbericht", nullToDash(report.getNotes()));
        model.put("incidentCommander", nullToDash(report.getIncidentCommander()));
        model.put("submittedInfo", formatSubmittedInfo(report, reportId));
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
            Map<Long, String> equipmentByVehicleId,
            List<EinsatzberichtPdfPersonRow> personnel,
            List<EinsatzberichtPdfVehicleRow> vehicles,
            boolean includeVehicleRow) {
        if (vehicle == null || vehicle.crewPersons() == null) {
            return;
        }
        String vehicleLabel = vehicle.name();
        for (KraefteFahrzeugeState.KraeftePersonView person : vehicle.crewPersons()) {
            String name = person.displayName();
            if (person.unitLabel() != null && !person.unitLabel().isBlank()) {
                name = name + " (" + person.unitLabel() + ")";
            }
            personnel.add(new EinsatzberichtPdfPersonRow(name, vehicleLabel, person.usesPa() ? "X" : ""));
        }
        if (includeVehicleRow && vehicle.vehicleId() > 0) {
            String maschinist = findRoleName(vehicle.crewPersons(), "MASCHINIST");
            String einheitsfuehrer = findRoleName(vehicle.crewPersons(), "EINHEITSFUEHRER");
            vehicles.add(new EinsatzberichtPdfVehicleRow(
                    vehicle.name(),
                    maschinist,
                    einheitsfuehrer,
                    vehicle.besatzungsstaerke(),
                    equipmentByVehicleId.getOrDefault(vehicle.vehicleId(), "—")));
        }
    }

    private static String findRoleName(List<KraefteFahrzeugeState.KraeftePersonView> crew, String role) {
        return crew.stream()
                .filter(person -> role.equals(person.vehicleRole()))
                .map(KraefteFahrzeugeState.KraeftePersonView::displayName)
                .findFirst()
                .orElse("—");
    }

    private Map<Long, String> loadEquipmentNames(long reportId) {
        Map<Long, List<String>> namesByVehicle = new LinkedHashMap<>();
        for (IncidentReportEquipment row : incidentReportEquipmentRepository.findByIncidentReportId(reportId)) {
            if (row.getVehicle() == null) {
                continue;
            }
            String name = row.getEquipmentName() != null ? row.getEquipmentName() : "Gerät";
            namesByVehicle.computeIfAbsent(row.getVehicle().getId(), ignored -> new ArrayList<>()).add(name);
        }
        return namesByVehicle.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(", ", e.getValue())));
    }

    private String formatSubmittedInfo(IncidentReport report, long reportId) {
        Instant when = resolveSubmittedAt(report);
        String who = resolveSubmittedByName(report, reportId);
        if (when == null) {
            return "Eingereicht von " + who;
        }
        return "Eingereicht am " + SUBMITTED_FMT.format(when) + " von " + who;
    }

    private Instant resolveSubmittedAt(IncidentReport report) {
        if ((report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                        || report.getStatus() == IncidentReportStatus.ARCHIVIERT)
                && report.getReleasedAt() != null) {
            return report.getReleasedAt();
        }
        return report.getUpdatedAt() != null ? report.getUpdatedAt() : report.getCreatedAt();
    }

    private String resolveSubmittedByName(IncidentReport report, long reportId) {
        if ((report.getStatus() == IncidentReportStatus.FREIGEGEBEN
                        || report.getStatus() == IncidentReportStatus.ARCHIVIERT)
                && report.getReleasedByUser() != null) {
            return formatUserName(report.getReleasedByUser());
        }
        return incidentReportChangeRepository
                .findFirstByIncidentReport_IdOrderByCreatedAtDescIdDesc(reportId)
                .map(IncidentReportChange::getChangedByName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> {
                    if (report.getCreatedByName() != null && !report.getCreatedByName().isBlank()) {
                        return report.getCreatedByName().trim();
                    }
                    return "Unbekannt";
                });
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
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return new int[] {0, 0, 0};
        }
    }

    private static String formatAddress(IncidentReport report) {
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

    private static String formatJaNein(Boolean value) {
        if (value == null) {
            return "—";
        }
        return value ? "Ja" : "Nein";
    }

    private static String formatPersonDamages(IncidentReport report) {
        if (!report.isPersonDamagesEnabled()) {
            return "Nein";
        }
        List<String> parts = new ArrayList<>();
        parts.add("Ja");
        if (report.getPersonsInjured() > 0) {
            parts.add(report.getPersonsInjured() + " verletzt");
        }
        if (report.getPersonsDead() > 0) {
            parts.add(report.getPersonsDead() + " tot");
        }
        if (report.getPersonsRescued() > 0) {
            parts.add(report.getPersonsRescued() + " gerettet");
        }
        if (parts.size() == 1) {
            return "Ja";
        }
        return String.join(", ", parts);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    public record EinsatzberichtPdfPersonRow(String name, String vehicle, String pa) {}

    public record EinsatzberichtPdfVehicleRow(
            String vehicle, String maschinist, String einheitsfuehrer, String strength, String equipment) {}
}
