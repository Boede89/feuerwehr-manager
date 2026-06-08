package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "incident_reports")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "incident_number", length = 32)
    private String incidentNumber;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "alarm_time")
    private LocalTime alarmTime;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "incident_type_key", nullable = false, length = 64)
    private String incidentTypeKey = "SONSTIGES";

    @Column(name = "incident_type_label", nullable = false)
    private String incidentTypeLabel = "Sonstiges";

    @Column(length = 255)
    private String stichwort;

    @Column(nullable = false, length = 300)
    private String location;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(length = 128)
    private String district;

    @Column(length = 255)
    private String street;

    @Column(name = "house_number", length = 20)
    private String houseNumber;

    @Column(length = 255)
    private String objekt;

    @Column(length = 255)
    private String eigentuemer;

    @Column(name = "extinguished_before_arrival", nullable = false)
    private boolean extinguishedBeforeArrival;

    @Column(name = "malicious_alarm", nullable = false)
    private boolean maliciousAlarm;

    @Column(name = "false_alarm", nullable = false)
    private boolean falseAlarm;

    @Column(nullable = false)
    private boolean supraregional;

    @Column(name = "bf_involved", nullable = false)
    private boolean bfInvolved;

    @Column(name = "violence_against_crew", nullable = false)
    private boolean violenceAgainstCrew;

    @Column(name = "violence_count", nullable = false)
    private int violenceCount;

    @Column(name = "incident_commander")
    private String incidentCommander;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commander_person_id")
    private Person commanderPerson;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "reporter_phone", length = 64)
    private String reporterPhone;

    @Column(name = "strength_leadership", nullable = false)
    private int strengthLeadership;

    @Column(name = "strength_sub", nullable = false)
    private int strengthSub;

    @Column(name = "strength_crew", nullable = false)
    private int strengthCrew;

    @Column(name = "fire_object", length = 512)
    private String fireObject;

    @Column(columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String measures;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "thl_type")
    private String thlType;

    @Column(name = "weather_influence")
    private String weatherInfluence;

    @Column(name = "handover_to")
    private String handoverTo;

    @Column(name = "handover_notes", columnDefinition = "TEXT")
    private String handoverNotes;

    @Column(name = "police_case_number", length = 128)
    private String policeCaseNumber;

    @Column(name = "police_station")
    private String policeStation;

    @Column(name = "police_officer")
    private String policeOfficer;

    @Column(name = "persons_rescued", nullable = false)
    private int personsRescued;

    @Column(name = "persons_evacuated", nullable = false)
    private int personsEvacuated;

    @Column(name = "persons_injured", nullable = false)
    private int personsInjured;

    @Column(name = "persons_injured_own", nullable = false)
    private int personsInjuredOwn;

    @Column(name = "persons_recovered", nullable = false)
    private int personsRecovered;

    @Column(name = "persons_dead", nullable = false)
    private int personsDead;

    @Column(name = "persons_dead_own", nullable = false)
    private int personsDeadOwn;

    @Column(name = "animals_rescued", nullable = false)
    private int animalsRescued;

    @Column(name = "animals_injured", nullable = false)
    private int animalsInjured;

    @Column(name = "animals_recovered", nullable = false)
    private int animalsRecovered;

    @Column(name = "animals_dead", nullable = false)
    private int animalsDead;

    @Column(name = "vehicle_damage", columnDefinition = "TEXT")
    private String vehicleDamage;

    @Column(name = "equipment_damage", columnDefinition = "TEXT")
    private String equipmentDamage;

    @Column(name = "resources_json", nullable = false, columnDefinition = "TEXT")
    private String resourcesJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentReportStatus status = IncidentReportStatus.ENTWURF;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_by_name")
    private String createdByName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_by_user_id")
    private User releasedByUser;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "divera_alarm_id")
    private Long diveraAlarmId;

    @Column(name = "divera_foreign_id", length = 128)
    private String diveraForeignId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
