package de.feuerwehr.manager.drivinglicense;

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
@Table(name = "driving_license_reminder_log")
public class DrivingLicenseReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private DrivingLicense license;

    @Enumerated(EnumType.STRING)
    @Column(name = "mail_kind", nullable = false, length = 16)
    private DrivingLicenseReminderMailKind mailKind;

    @Column(name = "next_due_on", nullable = false)
    private LocalDate nextDueOn;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "person_notified", nullable = false)
    private boolean personNotified;
}
