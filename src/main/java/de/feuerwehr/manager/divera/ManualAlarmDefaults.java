package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.berichte.UnitAddressSupport;
import de.feuerwehr.manager.berichte.UnitPostalCity;
import de.feuerwehr.manager.unit.Unit;
import java.util.regex.Pattern;

public final class ManualAlarmDefaults {

    private static final Pattern UNIT_PREFIX =
            Pattern.compile("^(?i)(löschzug|loeschzug|swt|ff|feuerwehr|wehr)\\s+");

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
        String district = deriveDistrict(unit, postal);
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
        if (postal != null && postal.city() != null && !postal.city().isBlank()) {
            String[] tokens = postal.city().trim().split("\\s+");
            if (tokens.length >= 2) {
                return tokens[tokens.length - 1];
            }
        }
        String unitName = unit != null && unit.getName() != null ? unit.getName().trim() : "";
        if (unitName.isBlank()) {
            return null;
        }
        String stripped = UNIT_PREFIX.matcher(unitName).replaceFirst("").trim();
        if (!stripped.isBlank()) {
            String[] parts = stripped.split("\\s+");
            return parts[parts.length - 1];
        }
        return unitName;
    }

    static String deriveDistrict(Unit unit) {
        return deriveDistrict(unit, UnitPostalCity.fromUnit(unit));
    }
}
