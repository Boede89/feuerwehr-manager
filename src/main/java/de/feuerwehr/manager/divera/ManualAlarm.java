package de.feuerwehr.manager.divera;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "manual_alarms")
public class ManualAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "alarm_id", nullable = false)
    private long alarmId;

    @Column(name = "alarm_number", length = 64)
    private String alarmNumber;

    @Column(name = "incident_category", length = 128)
    private String incidentCategory;

    @Column(nullable = false, length = 512)
    private String title = "";

    @Column(name = "alarm_text", columnDefinition = "TEXT")
    private String alarmText;

    @Column(name = "meldebild_zusatz", length = 512)
    private String meldebildZusatz;

    @Column(length = 512)
    private String address;

    @Column(length = 255)
    private String street;

    @Column(name = "house_number", length = 64)
    private String houseNumber;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String district;

    @Column(name = "object_name", length = 255)
    private String objectName;

    @Column(name = "reporter_name", length = 255)
    private String reporterName;

    @Column(name = "reporter_phone", length = 64)
    private String reporterPhone;

    @Column(name = "callback_phone", length = 64)
    private String callbackPhone;

    @Column(length = 128)
    private String meldeweg;

    @Column(name = "beteiligte_einsatzmittel", length = 512)
    private String beteiligteEinsatzmittel;

    @Column(name = "route_info", columnDefinition = "TEXT")
    private String routeInfo;

    @Column(name = "route_start_address", length = 512)
    private String routeStartAddress;

    @Column(name = "route_plan_use_geraetehaus", nullable = false)
    private boolean routePlanUseGeraetehaus = true;

    @Column(name = "route_plan_start_address", length = 512)
    private String routePlanStartAddress;

    @Column(name = "route_distance_m")
    private Integer routeDistanceM;

    @Column(name = "route_duration_sec")
    private Integer routeDurationSec;

    @Column(name = "route_avg_speed_kmh", precision = 6, scale = 1)
    private BigDecimal routeAvgSpeedKmh;

    @Column(name = "route_steps_json", columnDefinition = "TEXT")
    private String routeStepsJson;

    @Column(name = "route_title", length = 255)
    private String routeTitle;

    @Column(name = "leitstelle_name", length = 255)
    private String leitstelleName;

    @Column(name = "leitstelle_address", length = 512)
    private String leitstelleAddress;

    @Column(name = "leitstelle_phone", length = 64)
    private String leitstellePhone;

    @Column(name = "leitstelle_email", length = 255)
    private String leitstelleEmail;

    @Column(name = "date_epoch_seconds", nullable = false)
    private long dateEpochSeconds;

    @Column(name = "ts_create_seconds", nullable = false)
    private long tsCreateSeconds;

    @Column(nullable = false)
    private boolean closed;

    @Column(nullable = false)
    private boolean started;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(nullable = false)
    private boolean exercise;

    @Column(nullable = false)
    private boolean sondersignal = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
}
