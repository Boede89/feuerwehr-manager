package de.feuerwehr.manager.reservierungen;

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
@Table(name = "unit_reservierungen_settings")
public class UnitReservierungenSettings {

    @Id
    @Column(name = "unit_id")
    private Long unitId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "vehicle_sort_mode", nullable = false, length = 16)
    private String vehicleSortMode = "manual";

    @Column(name = "vehicle_divera_enabled", nullable = false)
    private boolean vehicleDiveraEnabled;

    @Column(name = "vehicle_google_calendar_enabled", nullable = false)
    private boolean vehicleGoogleCalendarEnabled;

    @Column(name = "vehicle_divera_default_group_id", length = 32)
    private String vehicleDiveraDefaultGroupId;

    @Column(name = "vehicle_divera_groups_json", columnDefinition = "TEXT")
    private String vehicleDiveraGroupsJson;

    @Column(name = "vehicle_loesch_warn_enabled", nullable = false)
    private boolean vehicleLoeschWarnEnabled;

    @Column(name = "vehicle_loesch_min_available", nullable = false)
    private int vehicleLoeschMinAvailable = 1;

    @Column(name = "vehicle_loesch_vehicle_ids_json", columnDefinition = "TEXT")
    private String vehicleLoeschVehicleIdsJson;

    @Column(name = "vehicle_notification_user_ids_json", columnDefinition = "TEXT")
    private String vehicleNotificationUserIdsJson;

    @Column(name = "room_sort_mode", nullable = false, length = 16)
    private String roomSortMode = "manual";

    @Column(name = "room_divera_enabled", nullable = false)
    private boolean roomDiveraEnabled;

    @Column(name = "room_google_calendar_enabled", nullable = false)
    private boolean roomGoogleCalendarEnabled;

    @Column(name = "room_divera_default_group_id", length = 32)
    private String roomDiveraDefaultGroupId;

    @Column(name = "room_notification_user_ids_json", columnDefinition = "TEXT")
    private String roomNotificationUserIdsJson;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
