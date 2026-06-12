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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "equipment_maintenance_reports")
public class EquipmentMaintenanceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GeraetewartTyp typ = GeraetewartTyp.UEBUNG;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GeraetewartReadiness readiness = GeraetewartReadiness.HERGESTELLT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_person_id")
    private Person leaderPerson;

    @Column(name = "leader_name", length = 255)
    private String leaderName;

    @Column(name = "deployed_equipment_json", columnDefinition = "TEXT")
    private String deployedEquipmentJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "created_by_name", length = 255)
    private String createdByName;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
