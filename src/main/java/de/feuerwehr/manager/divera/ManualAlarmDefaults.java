package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.berichte.UnitAddressSupport;
import de.feuerwehr.manager.berichte.UnitPostalCity;
import de.feuerwehr.manager.unit.Unit;

public final class ManualAlarmDefaults {

    private ManualAlarmDefaults() {}

    public record FormDefaults(
            String postalCode,
            String city,
            String district,
            String geraetehausAddress,
            String leitstelleName,
            String leitstelleAddress,
            String beteiligteEinsatzmittel) {}

    public static FormDefaults forUnit(Unit unit) {
        if (unit == null) {
            return new FormDefaults(null, null, null, "", "Zentrale", "", "|| ");
        }
        UnitPostalCity.Parts postal = UnitPostalCity.fromUnit(unit);
        String unitName = unit.getName() != null ? unit.getName().trim() : "";
        String district = unitName.isBlank() ? null : unitName;
        String geraetehaus = UnitAddressSupport.fullAddressLine(unit);
        String leitstelleName = unitName.isBlank() ? "Zentrale" : "Zentrale " + unitName;
        String beteiligte = unitName.isBlank() ? "|| " : "|| " + unitName;
        return new FormDefaults(
                postal.postalCode(),
                postal.city(),
                district,
                geraetehaus,
                leitstelleName,
                geraetehaus,
                beteiligte);
    }
}
