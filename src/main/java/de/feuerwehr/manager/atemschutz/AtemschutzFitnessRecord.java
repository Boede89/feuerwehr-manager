package de.feuerwehr.manager.atemschutz;

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
import de.feuerwehr.manager.user.User;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "atemschutz_fitness_records")
public class AtemschutzFitnessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrier_id", nullable = false)
    private AtemschutzCarrier carrier;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 32)
    private AtemschutzFitnessType recordType;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(length = 255)
    private String physician;

    @Column(name = "result_notes", length = 1024)
    private String resultNotes;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    /** Anzeigename des Bezugs (z. B. Einsatzstichwort), für spätere Automatik befüllbar. */
    @Column(name = "source_label", length = 255)
    private String sourceLabel;

    /** Typ des verknüpften Datensatzes (z. B. ATTENDANCE), für spätere Automatik. */
    @Column(name = "source_ref_type", length = 32)
    private String sourceRefType;

    /** ID des verknüpften Datensatzes (z. B. person_attendance.id), für spätere Automatik. */
    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
