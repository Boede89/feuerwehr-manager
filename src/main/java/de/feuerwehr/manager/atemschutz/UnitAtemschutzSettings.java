package de.feuerwehr.manager.atemschutz;

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
@Table(name = "unit_atemschutz_settings")
public class UnitAtemschutzSettings {

    @Id
    @Column(name = "unit_id")
    private Long unitId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "warn_days", nullable = false)
    private int warnDays = 90;

    @Column(name = "agt_course_name", nullable = false, length = 64)
    private String agtCourseName = "AGT";

    @Column(name = "notification_user_ids", columnDefinition = "TEXT")
    private String notificationUserIds;

    @Column(name = "cc_user_ids", columnDefinition = "TEXT")
    private String ccUserIds;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
