package de.feuerwehr.manager.personal;

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
@Table(name = "persons")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(name = "email_private", length = 255)
    private String emailPrivate;

    @Column(length = 50)
    private String phone;

    @Column(length = 200)
    private String address;

    private LocalDate birthdate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qualification_type_id")
    private QualificationType qualificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PersonStatus status = PersonStatus.ACTIVE;

    @Column(name = "test_data", nullable = false)
    private boolean testData;

    @Column(name = "production_source_id")
    private Long productionSourceId;

    @Column(name = "divera_ucr_id", length = 64)
    private String diveraUcrId;

    @Column(length = 512)
    private String notes;

    @Column(name = "personnel_number", length = 50)
    private String personnelNumber;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_updated_by_id")
    private User profileUpdatedBy;

    @Column(name = "profile_updated_by_name", length = 255)
    private String profileUpdatedByName;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public String displayName() {
        return formatDisplayName(firstName, lastName);
    }

    public static String formatDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        return (last + " " + first).trim();
    }
}
