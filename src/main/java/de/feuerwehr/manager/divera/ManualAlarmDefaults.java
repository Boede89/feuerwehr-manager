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
        String district = UnitAddressSupport.deriveDistrict(unit, postal);
        String geraetehaus = UnitAddressSupport.fullAddressLine(unit);
        String leitstelleName = district != null && !district.isBlank()
                ? "Zentrale " + district
                : (unitName.isBlank() ? "Zentrale" : "Zentrale " + unitName);
        String beteiligte = district != null && !district.isBlank() ? "|| " + district : "|| ";
        return new FormDefaults(
                postal.postalCode(),
                postal.city(),
                district,
                geraetehaus,
                leitstelleName,
                geraetehaus,
                beteiligte);
    }

    /** Ortsteil z. B. „Amern“ statt „Löschzug Amern“. */
    static String deriveDistrict(Unit unit, UnitPostalCity.Parts postal) {
        return UnitAddressSupport.deriveDistrict(unit, postal);
    }

    static String deriveDistrict(Unit unit) {
        return UnitAddressSupport.deriveDistrict(unit);
    }
}
