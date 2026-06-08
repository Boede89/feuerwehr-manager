package de.feuerwehr.manager.atemschutz;

import de.feuerwehr.manager.personal.Course;
import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(name = "g26_warn_days", nullable = false)
    private int g26WarnDays = 90;

    @Column(name = "strecke_warn_days", nullable = false)
    private int streckeWarnDays = 90;

    @Column(name = "uebung_warn_days", nullable = false)
    private int uebungWarnDays = 90;

    @Column(name = "g26_notify_instructors", nullable = false)
    private boolean g26NotifyInstructors;

    @Column(name = "strecke_notify_instructors", nullable = false)
    private boolean streckeNotifyInstructors;

    @Column(name = "uebung_notify_instructors", nullable = false)
    private boolean uebungNotifyInstructors;

    @Column(name = "g26_notify_carriers", nullable = false)
    private boolean g26NotifyCarriers;

    @Column(name = "strecke_notify_carriers", nullable = false)
    private boolean streckeNotifyCarriers;

    @Column(name = "uebung_notify_carriers", nullable = false)
    private boolean uebungNotifyCarriers;

    @Column(name = "g26_cc_person_ids", columnDefinition = "TEXT")
    private String g26CcPersonIds;

    @Column(name = "strecke_cc_person_ids", columnDefinition = "TEXT")
    private String streckeCcPersonIds;

    @Column(name = "uebung_cc_person_ids", columnDefinition = "TEXT")
    private String uebungCcPersonIds;

    @Column(name = "instructor_user_ids", columnDefinition = "TEXT")
    private String instructorUserIds;

    @Column(name = "agt_course_name", nullable = false, length = 64)
    private String agtCourseName = "AGT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agt_course_id")
    private Course agtCourse;

    @Column(name = "notification_user_ids", columnDefinition = "TEXT")
    private String notificationUserIds;

    @Column(name = "cc_user_ids", columnDefinition = "TEXT")
    private String ccUserIds;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
