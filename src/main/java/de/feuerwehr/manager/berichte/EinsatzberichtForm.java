package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class EinsatzberichtForm {

    private String incidentNumber;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate incidentDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime alarmTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime departureTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime arrivalTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;
    private String stichwort;
    private String nachrichtLeitstelle;
    private String location;
    private String postalCode;
    private String district;
    private String street;
    private String houseNumber;
    private String objekt;
    private String eigentuemer;
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
    private String crewAssignmentsJson;
    private String einsatzkurzbericht;
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

    public static EinsatzberichtForm fromReport(IncidentReport report) {
        EinsatzberichtForm form = new EinsatzberichtForm();
        form.setIncidentNumber(report.getIncidentNumber());
        form.setIncidentDate(report.getIncidentDate());
        form.setAlarmTime(report.getAlarmTime());
        form.setDepartureTime(report.getDepartureTime());
        form.setArrivalTime(report.getArrivalTime());
        form.setEndTime(report.getEndTime());
        form.setStichwort(report.getStichwort() != null ? report.getStichwort() : report.getIncidentTypeLabel());
        form.setNachrichtLeitstelle(report.getSituation());
        form.setLocation(report.getLocation());
        form.setPostalCode(report.getPostalCode());
        form.setDistrict(report.getDistrict());
        form.setStreet(report.getStreet());
        form.setHouseNumber(report.getHouseNumber());
        form.setObjekt(report.getObjekt());
        form.setEigentuemer(report.getEigentuemer());
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
        form.setEinsatzkurzbericht(report.getNotes());
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

    public EinsatzberichtFormData toData(List<CrewAssignment> crewAssignments) {
        return new EinsatzberichtFormData(
                incidentNumber,
                incidentDate,
                alarmTime,
                departureTime,
                arrivalTime,
                endTime,
                stichwort,
                nachrichtLeitstelle,
                location,
                postalCode,
                district,
                street,
                houseNumber,
                objekt,
                eigentuemer,
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
                crewAssignments != null ? crewAssignments : List.of(),
                einsatzkurzbericht,
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
                equipmentDamage);
    }
}
