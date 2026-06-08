package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EinsatzberichtForm {

    private String incidentNumber;
    private LocalDate incidentDate;
    private LocalTime alarmTime;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private LocalTime endTime;
    private String incidentTypeKey = "SONSTIGES";
    private String incidentTypeLabel = "Sonstiges";
    private String location;
    private String postalCode;
    private String district;
    private String street;
    private String houseNumber;
    private boolean extinguishedBeforeArrival;
    private boolean maliciousAlarm;
    private boolean falseAlarm;
    private boolean supraregional;
    private boolean bfInvolved;
    private boolean violenceAgainstCrew;
    private int violenceCount;
    private String incidentCommander;
    private String reporterName;
    private String reporterPhone;
    private int strengthLeadership;
    private int strengthSub;
    private int strengthCrew;
    private String fireObject;
    private String situation;
    private String measures;
    private String notes;
    private String weatherInfluence;
    private String handoverTo;
    private String handoverNotes;
    private String policeCaseNumber;
    private String policeStation;
    private String policeOfficer;
    private int personsRescued;
    private int personsEvacuated;
    private int personsInjured;
    private int personsInjuredOwn;
    private int personsRecovered;
    private int personsDead;
    private int personsDeadOwn;
    private int animalsRescued;
    private int animalsInjured;
    private int animalsRecovered;
    private int animalsDead;
    private String vehicleDamage;
    private String equipmentDamage;

    public static EinsatzberichtForm fromReport(IncidentReport report, Map<String, Object> resources) {
        EinsatzberichtForm form = new EinsatzberichtForm();
        form.setIncidentNumber(report.getIncidentNumber());
        form.setIncidentDate(report.getIncidentDate());
        form.setAlarmTime(report.getAlarmTime());
        form.setDepartureTime(report.getDepartureTime());
        form.setArrivalTime(report.getArrivalTime());
        form.setEndTime(report.getEndTime());
        form.setIncidentTypeKey(report.getIncidentTypeKey());
        form.setIncidentTypeLabel(report.getIncidentTypeLabel());
        form.setLocation(report.getLocation());
        form.setPostalCode(report.getPostalCode());
        form.setDistrict(report.getDistrict());
        form.setStreet(report.getStreet());
        form.setHouseNumber(report.getHouseNumber());
        form.setExtinguishedBeforeArrival(report.isExtinguishedBeforeArrival());
        form.setMaliciousAlarm(report.isMaliciousAlarm());
        form.setFalseAlarm(report.isFalseAlarm());
        form.setSupraregional(report.isSupraregional());
        form.setBfInvolved(report.isBfInvolved());
        form.setViolenceAgainstCrew(report.isViolenceAgainstCrew());
        form.setViolenceCount(report.getViolenceCount());
        form.setIncidentCommander(report.getIncidentCommander());
        form.setReporterName(report.getReporterName());
        form.setReporterPhone(report.getReporterPhone());
        form.setStrengthLeadership(report.getStrengthLeadership());
        form.setStrengthSub(report.getStrengthSub());
        form.setStrengthCrew(report.getStrengthCrew());
        form.setFireObject(report.getFireObject());
        form.setSituation(report.getSituation());
        form.setMeasures(report.getMeasures());
        form.setNotes(report.getNotes());
        form.setWeatherInfluence(report.getWeatherInfluence());
        form.setHandoverTo(report.getHandoverTo());
        form.setHandoverNotes(report.getHandoverNotes());
        form.setPoliceCaseNumber(report.getPoliceCaseNumber());
        form.setPoliceStation(report.getPoliceStation());
        form.setPoliceOfficer(report.getPoliceOfficer());
        form.setPersonsRescued(report.getPersonsRescued());
        form.setPersonsEvacuated(report.getPersonsEvacuated());
        form.setPersonsInjured(report.getPersonsInjured());
        form.setPersonsInjuredOwn(report.getPersonsInjuredOwn());
        form.setPersonsRecovered(report.getPersonsRecovered());
        form.setPersonsDead(report.getPersonsDead());
        form.setPersonsDeadOwn(report.getPersonsDeadOwn());
        form.setAnimalsRescued(report.getAnimalsRescued());
        form.setAnimalsInjured(report.getAnimalsInjured());
        form.setAnimalsRecovered(report.getAnimalsRecovered());
        form.setAnimalsDead(report.getAnimalsDead());
        form.setVehicleDamage(report.getVehicleDamage());
        form.setEquipmentDamage(report.getEquipmentDamage());
        return form;
    }

    public EinsatzberichtFormData toData(Map<String, String> resources) {
        return new EinsatzberichtFormData(
                incidentNumber,
                incidentDate,
                alarmTime,
                departureTime,
                arrivalTime,
                endTime,
                incidentTypeKey,
                incidentTypeLabel,
                location,
                postalCode,
                district,
                street,
                houseNumber,
                extinguishedBeforeArrival,
                maliciousAlarm,
                falseAlarm,
                supraregional,
                bfInvolved,
                violenceAgainstCrew,
                violenceCount,
                incidentCommander,
                reporterName,
                reporterPhone,
                strengthLeadership,
                strengthSub,
                strengthCrew,
                fireObject,
                situation,
                measures,
                notes,
                weatherInfluence,
                handoverTo,
                handoverNotes,
                policeCaseNumber,
                policeStation,
                policeOfficer,
                personsRescued,
                personsEvacuated,
                personsInjured,
                personsInjuredOwn,
                personsRecovered,
                personsDead,
                personsDeadOwn,
                animalsRescued,
                animalsInjured,
                animalsRecovered,
                animalsDead,
                vehicleDamage,
                equipmentDamage,
                resources);
    }
}
