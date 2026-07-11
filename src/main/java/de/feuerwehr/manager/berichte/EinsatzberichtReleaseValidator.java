package de.feuerwehr.manager.berichte;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pflichtfelder vor Freigabe eines Einsatzberichts. */
public final class EinsatzberichtReleaseValidator {

    private EinsatzberichtReleaseValidator() {}

    public static EinsatzberichtReleaseValidationResult validate(
            IncidentReport report, KraefteFahrzeugeState state) {
        List<EinsatzberichtReleaseFieldIssue> issues = new ArrayList<>();
        if (report.getIncidentDate() == null) {
            issues.add(issue("incidentDate", "Datum", 0, "incidentDate"));
        }
        if (report.getAlarmTime() == null) {
            issues.add(issue("alarmTime", "Alarmzeit", 0, "alarmTime"));
        }
        if (report.getEndTime() == null) {
            issues.add(issue("endTime", "Einsatzende", 0, "endTime"));
        }
        if (isBlank(resolveStichwort(report))) {
            issues.add(issue("stichwort", "Stichwort", 0, "stichwort"));
        }
        if (isBlank(report.getAlarmierungDurch())) {
            issues.add(issue("alarmierungDurch", "Alarmierung durch", 0, "alarmierungDurch"));
        }
        if (isBlank(report.getLocation())) {
            issues.add(issue("location", "Einsatzort", 0, "location"));
        }
        if (isBlank(report.getPostalCode())) {
            issues.add(issue("postalCode", "PLZ", 0, "postalCode"));
        }
        if (isBlank(report.getIncidentCommander())) {
            issues.add(issue("incidentCommander", "Einsatzleiter", 0, "incidentCommander"));
        }
        if (state != null) {
            if (countTotalPersonnel(state) < 1) {
                issues.add(issue("personnel", "Am Einsatz beteiligtes Personal", 1, "personal-involved-slot"));
            }
            KraefteFahrzeugeState.KraefteVehicleView beteiligt = state.beteiligt();
            if (beteiligt != null
                    && beteiligt.crewPersonIds() != null
                    && !beteiligt.crewPersonIds().isEmpty()) {
                issues.add(issue(
                        "vehicleAssignments",
                        "Fahrzeugzuordnungen (noch Personal ohne Fahrzeug)",
                        2,
                        "incident-vehicle-stack"));
            }
        }
        return new EinsatzberichtReleaseValidationResult(issues.isEmpty(), List.copyOf(issues));
    }

    public static String formatErrorMessage(List<EinsatzberichtReleaseFieldIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "Freigabe nicht möglich.";
        }
        String labels = issues.stream().map(EinsatzberichtReleaseFieldIssue::label).reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "Freigabe nicht möglich. Folgende Pflichtfelder fehlen noch: " + labels;
    }

    private static EinsatzberichtReleaseFieldIssue issue(
            String key, String label, int tabIndex, String anchorId) {
        return new EinsatzberichtReleaseFieldIssue(key, label, tabIndex, anchorId);
    }

    private static String resolveStichwort(IncidentReport report) {
        if (report.getStichwort() != null && !report.getStichwort().isBlank()) {
            return report.getStichwort();
        }
        return report.getIncidentTypeLabel();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int countTotalPersonnel(KraefteFahrzeugeState state) {
        Set<Long> ids = new LinkedHashSet<>();
        collectPersonIds(state.beteiligt(), ids);
        collectPersonIds(state.einsatzstelle(), ids);
        collectPersonIds(state.wache(), ids);
        if (state.vehicles() != null) {
            for (KraefteFahrzeugeState.KraefteVehicleView vehicle : state.vehicles()) {
                collectPersonIds(vehicle, ids);
            }
        }
        return ids.size();
    }

    private static void collectPersonIds(KraefteFahrzeugeState.KraefteVehicleView vehicle, Set<Long> ids) {
        if (vehicle == null || vehicle.crewPersonIds() == null) {
            return;
        }
        vehicle.crewPersonIds().stream().filter(id -> id != null && id > 0).forEach(ids::add);
    }
}
