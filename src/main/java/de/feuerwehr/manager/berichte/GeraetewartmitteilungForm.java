package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GeraetewartmitteilungForm {

    private String typ = GeraetewartTyp.UEBUNG.name();
    private LocalDate eventDate = LocalDate.now();
    private String readiness = GeraetewartReadiness.HERGESTELLT.name();
    private Long leaderPersonId;
    private String leaderName;
    private String deployedEquipmentJson = "[]";
}
