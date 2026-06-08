package de.feuerwehr.manager.berichte;

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
@Table(name = "unit_berichte_settings")
public class UnitBerichteSettings {

    @Id
    @Column(name = "unit_id")
    private Long unitId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "import_incident_data_from_divera", nullable = false)
    private boolean importIncidentDataFromDivera;

    @Column(name = "import_personnel_from_divera", nullable = false)
    private boolean importPersonnelFromDivera;

    @Column(name = "einsatz_personnel_status_ids", columnDefinition = "TEXT")
    private String einsatzPersonnelStatusIds;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
