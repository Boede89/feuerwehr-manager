package de.feuerwehr.manager.drivinglicense;

import de.feuerwehr.manager.personal.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "driving_licenses")
public class DrivingLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DrivingLicensePresence presence = DrivingLicensePresence.UNKNOWN;

    @Column(name = "classes_csv", length = 128)
    private String classesCsv;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "number_suffix", length = 16)
    private String numberSuffix;

    @Column(columnDefinition = "TEXT")
    private String restrictions;

    @Column(name = "self_report_acknowledged", nullable = false)
    private boolean selfReportAcknowledged;

    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
