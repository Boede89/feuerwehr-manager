package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaengelberichtForm {

    private String standort = MaengelberichtStandort.GH_AMERN.name();
    private String mangelAn = MaengelberichtMangelAn.GEBAEUDE.name();
    private String bezeichnung;
    private Long vehicleId;
    private String mangelBeschreibung;
    private String ursache;
    private String verbleib;
    private Long recordedPersonId;
    private String recordedByName;
    private LocalDate aufgenommenAm = LocalDate.now();
}
