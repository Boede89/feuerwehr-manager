package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.technik.Vehicle;
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
@Table(name = "defect_reports")
public class DefectReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private MaengelberichtStandort standort = MaengelberichtStandort.GH_AMERN;

    @Enumerated(EnumType.STRING)
    @Column(name = "mangel_an", nullable = false, length = 50)
    private MaengelberichtMangelAn mangelAn = MaengelberichtMangelAn.GEBAEUDE;

    @Column(length = 255)
    private String bezeichnung;

    @Column(name = "mangel_beschreibung", columnDefinition = "TEXT")
    private String mangelBeschreibung;

    @Column(columnDefinition = "TEXT")
    private String ursache;

    @Column(columnDefinition = "TEXT")
    private String verbleib;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_person_id")
    private Person recordedPerson;

    @Column(name = "recorded_by_text", length = 255)
    private String recordedByText;

    @Column(name = "aufgenommen_am", nullable = false)
    private LocalDate aufgenommenAm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

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
