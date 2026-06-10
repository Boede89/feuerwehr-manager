package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Feld-Snapshot für Änderungsprotokoll. */
public final class IncidentReportSnapshot {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("incidentNumber", "Einsatznummer"),
            Map.entry("incidentDate", "Datum"),
            Map.entry("alarmTime", "Alarmzeit"),
            Map.entry("endTime", "Einsatzende"),
            Map.entry("stichwort", "Stichwort"),
            Map.entry("alarmierungDurch", "Alarmierung durch"),
            Map.entry("nachrichtLeitstelle", "Nachricht Leitstelle"),
            Map.entry("location", "Einsatzort"),
            Map.entry("postalCode", "PLZ"),
            Map.entry("district", "Ortsteil"),
            Map.entry("street", "Straße"),
            Map.entry("houseNumber", "Hausnummer"),
            Map.entry("objekt", "Objekt"),
            Map.entry("eigentuemer", "Eigentümer"),
            Map.entry("chargeable", "Kostenpflichtig"),
            Map.entry("fireWatch", "Brandwache"),
            Map.entry("extinguishedBeforeArrival", "Vor Ankunft gelöscht"),
            Map.entry("maliciousAlarm", "Böswilliger Alarm"),
            Map.entry("falseAlarm", "Fehlalarm"),
            Map.entry("supraregional", "Überörtlich"),
            Map.entry("bfInvolved", "BF beteiligt"),
            Map.entry("violenceAgainstCrew", "Gewalt gegen Einsatzkräfte"),
            Map.entry("violenceCount", "Anzahl Gewaltfälle"),
            Map.entry("incidentCommander", "Einsatzleiter"),
            Map.entry("reporterName", "Meldender Name"),
            Map.entry("reporterPhone", "Meldender Telefon"),
            Map.entry("einsatzkurzbericht", "Einsatzkurzbericht"),
            Map.entry("personDamagesEnabled", "Personenschäden aktiv"),
            Map.entry("animalDamagesEnabled", "Tierschäden aktiv"),
            Map.entry("personsRescued", "Gerettete Personen"),
            Map.entry("personsInjured", "Verletzte Personen"),
            Map.entry("personsRecovered", "Geborgene Personen"),
            Map.entry("personsDead", "Tote Personen"),
            Map.entry("animalsRescued", "Gerettete Tiere"),
            Map.entry("animalsInjured", "Verletzte Tiere"),
            Map.entry("animalsRecovered", "Geborgene Tiere"),
            Map.entry("animalsDead", "Tote Tiere"),
            Map.entry("vehicleDamage", "Fahrzeugschäden"),
            Map.entry("equipmentDamage", "Geräteschäden"));

    private IncidentReportSnapshot() {}

    public record FieldChange(String key, String label, String oldValue, String newValue) {}

    public static Map<String, String> fromReport(IncidentReport report) {
        Map<String, String> map = new LinkedHashMap<>();
        if (report == null) {
            return map;
        }
        map.put("incidentNumber", norm(report.getIncidentNumber()));
        map.put("incidentDate", formatDate(report.getIncidentDate()));
        map.put("alarmTime", formatTime(report.getAlarmTime()));
        map.put("endTime", formatTime(report.getEndTime()));
        map.put("stichwort", norm(report.getStichwort()));
        map.put("alarmierungDurch", norm(report.getAlarmierungDurch()));
        map.put("nachrichtLeitstelle", norm(report.getSituation()));
        map.put("location", norm(report.getLocation()));
        map.put("postalCode", norm(report.getPostalCode()));
        map.put("district", norm(report.getDistrict()));
        map.put("street", norm(report.getStreet()));
        map.put("houseNumber", norm(report.getHouseNumber()));
        map.put("objekt", norm(report.getObjekt()));
        map.put("eigentuemer", norm(report.getEigentuemer()));
        map.put("chargeable", formatBool(report.getChargeable()));
        map.put("fireWatch", formatBool(report.getFireWatch()));
        map.put("extinguishedBeforeArrival", formatBool(report.isExtinguishedBeforeArrival()));
        map.put("maliciousAlarm", formatBool(report.isMaliciousAlarm()));
        map.put("falseAlarm", formatBool(report.isFalseAlarm()));
        map.put("supraregional", formatBool(report.isSupraregional()));
        map.put("bfInvolved", formatBool(report.isBfInvolved()));
        map.put("violenceAgainstCrew", formatBool(report.isViolenceAgainstCrew()));
        map.put("violenceCount", String.valueOf(report.getViolenceCount()));
        map.put("incidentCommander", norm(report.getIncidentCommander()));
        map.put("reporterName", norm(report.getReporterName()));
        map.put("reporterPhone", norm(report.getReporterPhone()));
        map.put("einsatzkurzbericht", norm(report.getNotes()));
        map.put("personDamagesEnabled", formatBool(report.isPersonDamagesEnabled()));
        map.put("animalDamagesEnabled", formatBool(report.isAnimalDamagesEnabled()));
        map.put("personsRescued", String.valueOf(report.getPersonsRescued()));
        map.put("personsInjured", String.valueOf(report.getPersonsInjured()));
        map.put("personsRecovered", String.valueOf(report.getPersonsRecovered()));
        map.put("personsDead", String.valueOf(report.getPersonsDead()));
        map.put("animalsRescued", String.valueOf(report.getAnimalsRescued()));
        map.put("animalsInjured", String.valueOf(report.getAnimalsInjured()));
        map.put("animalsRecovered", String.valueOf(report.getAnimalsRecovered()));
        map.put("animalsDead", String.valueOf(report.getAnimalsDead()));
        map.put("vehicleDamage", norm(report.getVehicleDamage()));
        map.put("equipmentDamage", norm(report.getEquipmentDamage()));
        return map;
    }

    public static List<FieldChange> diff(Map<String, String> before, Map<String, String> after) {
        List<FieldChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            String key = entry.getKey();
            String oldVal = before.getOrDefault(key, "");
            String newVal = after.getOrDefault(key, "");
            if (!Objects.equals(oldVal, newVal)) {
                changes.add(new FieldChange(key, entry.getValue(), display(oldVal), display(newVal)));
            }
        }
        return changes;
    }

    private static String norm(String value) {
        return value != null ? value.trim() : "";
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "";
    }

    private static String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FMT) : "";
    }

    private static String formatBool(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "Ja" : "Nein";
    }

    private static String formatBool(boolean value) {
        return value ? "Ja" : "Nein";
    }
}
