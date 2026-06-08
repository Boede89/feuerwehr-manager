package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "strecke_termine")
public class StreckeTermin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "termin_datum", nullable = false)
    private LocalDate terminDatum;

    @Column(name = "termin_zeit", nullable = false)
    private LocalTime terminZeit = LocalTime.of(9, 0);

    @Column(nullable = false, length = 255)
    private String ort = "";

    @Column(name = "max_teilnehmer", nullable = false)
    private int maxTeilnehmer = 10;

    @Column(columnDefinition = "TEXT")
    private String bemerkung;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
