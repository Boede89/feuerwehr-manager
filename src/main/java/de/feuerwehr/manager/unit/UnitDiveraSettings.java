package de.feuerwehr.manager.unit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Divera-Zugang pro Einheit (entspricht grob den früheren einheit_settings / Access Key).
 * Hinweis: access_key ist sensibel – später Verschlüsselung oder externes Secret-Management.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unit_divera_settings")
public class UnitDiveraSettings {

    @Id
    private Long unitId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "api_base_url", nullable = false, length = 512)
    private String apiBaseUrl = "https://app.divera247.com";

    @Column(name = "access_key", nullable = false, length = 2048)
    private String accessKey = "";
}
