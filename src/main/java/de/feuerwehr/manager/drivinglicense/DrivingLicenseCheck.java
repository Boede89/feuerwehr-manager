package de.feuerwehr.manager.drivinglicense;

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
@Table(name = "driving_license_checks")
public class DrivingLicenseCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private DrivingLicense license;

    @Column(name = "checked_on", nullable = false)
    private LocalDate checkedOn;

    @Column(name = "next_due_on", nullable = false)
    private LocalDate nextDueOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DrivingLicenseCheckResult result;

    @Column(name = "classes_csv", length = 128)
    private String classesCsv;

    @Column(length = 1024)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by_user_id")
    private User checkedBy;

    @Column(name = "checked_by_display_name", length = 255)
    private String checkedByDisplayName;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
