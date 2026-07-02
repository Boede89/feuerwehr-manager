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

    @Column(name = "einsatz_divera_personnel_auto_anwesenheit", nullable = false)
    private boolean autoAssignDiveraPersonnelToAnwesenheit;

    @Column(name = "allow_foreign_unit_personnel", nullable = false)
    private boolean allowForeignUnitPersonnel;

    @Column(name = "einsatz_personnel_status_ids", columnDefinition = "TEXT")
    private String einsatzPersonnelStatusIds;

    @Column(name = "einsatz_release_create_geraetewart", nullable = false)
    private boolean einsatzReleaseCreateGeraetewart;

    @Column(name = "einsatz_release_print_report", nullable = false)
    private boolean einsatzReleasePrintReport;

    @Column(name = "einsatz_release_print_geraetewart", nullable = false)
    private boolean einsatzReleasePrintGeraetewart;

    @Column(name = "einsatz_release_print_maengel", nullable = false)
    private boolean einsatzReleasePrintMaengel;

    @Column(name = "anwesenheit_release_print_report", nullable = false)
    private boolean anwesenheitReleasePrintReport;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
