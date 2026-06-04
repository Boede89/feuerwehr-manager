package de.feuerwehr.manager.unit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unit_calendar_settings")
public class UnitCalendarSettings {

    @Id
    @Column(name = "unit_id")
    private Long unitId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(length = 32)
    private String provider = "google";

    @Column(name = "calendar_url", length = 1024)
    private String calendarUrl;

    @Column(name = "calendar_id", length = 512)
    private String calendarId;

    /** Google Service-Account-JSON (Schreibrechte auf calendar_id). */
    @Column(name = "service_account_json", columnDefinition = "MEDIUMTEXT")
    private String serviceAccountJson;

    @Column(nullable = false)
    private boolean enabled;
}
