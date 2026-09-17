package de.feuerwehr.manager.drivinglicense;

import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unit_driving_license_settings")
public class UnitDrivingLicenseSettings {

    @Id
    @Column(name = "unit_id")
    private Long unitId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "interval_months", nullable = false)
    private int intervalMonths = 12;

    @Column(name = "warn_days", nullable = false)
    private int warnDays = 30;

    @Column(name = "notify_person", nullable = false)
    private boolean notifyPerson;

    @Column(name = "notification_user_ids", columnDefinition = "TEXT")
    private String notificationUserIds;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
