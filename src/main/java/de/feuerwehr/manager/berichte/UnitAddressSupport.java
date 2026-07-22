package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.unit.Unit;
import java.util.regex.Pattern;

public final class UnitAddressSupport {

    public static final String DEFAULT_OBJEKT_GERAETEHAUS = "Gerätehaus";

    private static final Pattern STREET_WITH_NUMBER = Pattern.compile(
            "^(.*?)(?:\\s+(\\d+(?:\\s+[a-zA-Z]|[a-zA-Z])?(?:\\s*-\\s*\\d+(?:\\s+[a-zA-Z]|[a-zA-Z])?)?))$");

    private static final Pattern UNIT_PREFIX =
            Pattern.compile("^(?i)(löschzug|loeschzug|swt|ff|feuerwehr|wehr)\\s+");

    private UnitAddressSupport() {}

    public record UnitAddress(
            String location, String postalCode, String street, String houseNumber, String district) {}

    public record StreetParts(String street, String houseNumber) {}

    public static UnitAddress fromUnit(Unit unit) {
        if (unit == null) {
            return new UnitAddress(null, null, null, null, null);
        }
        UnitPostalCity.Parts postal = UnitPostalCity.fromUnit(unit);
        StreetParts streetParts = parseStreet(unit.getStreet());
        return new UnitAddress(
                postal.city(),
                postal.postalCode(),
                streetParts.street(),
                streetParts.houseNumber(),
                deriveDistrict(unit, postal));
    }

    /** Ortsteil z. B. „Amern“ aus „Schwalmtal Amern“ bzw. Einheitsname „Löschzug Amern“. */
    public static String deriveDistrict(Unit unit) {
        return deriveDistrict(unit, UnitPostalCity.fromUnit(unit));
    }

    public static String deriveDistrict(Unit unit, UnitPostalCity.Parts postal) {
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

    /** Vollständige Adresszeile für Routing (Gerätehaus). */
    public static String fullAddressLine(Unit unit) {
        if (unit == null) {
            return "";
        }
        UnitAddress address = fromUnit(unit);
        StringBuilder sb = new StringBuilder();
        if (!isBlank(address.street())) {
            sb.append(address.street().trim());
            if (!isBlank(address.houseNumber())) {
                sb.append(' ').append(address.houseNumber().trim());
            }
        } else if (!isBlank(unit.getStreet())) {
            sb.append(unit.getStreet().trim());
        }
        if (!isBlank(address.postalCode())) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(address.postalCode().trim());
        }
        if (!isBlank(address.location())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(address.location().trim());
        }
        return sb.toString().trim();
    }

    public static void applyDefaultsToForm(EinsatzberichtForm form, Unit unit) {
        applyDefaultsToFormIfBlank(form, unit);
    }

    /**
     * Anwesenheitsliste: nur PLZ vorbelegen. Ort/Straße/Objekt/Ortsteil kommen über den
     * Gerätehaus-Button oder manuelle Eingabe — leere Felder werden nicht erneut befüllt.
     */
    public static void applyDefaultsToFormIfBlank(EinsatzberichtForm form, Unit unit) {
        if (form == null || unit == null) {
            return;
        }
        UnitAddress address = fromUnit(unit);
        if (isBlank(form.getPostalCode()) && !isBlank(address.postalCode())) {
            form.setPostalCode(address.postalCode());
        }
    }

    /** @see #applyDefaultsToFormIfBlank(EinsatzberichtForm, Unit) */
    public static void applyDefaultsToReportIfBlank(AttendanceReport report, Unit unit) {
        if (report == null || unit == null) {
            return;
        }
        UnitAddress address = fromUnit(unit);
        if (isBlank(report.getPostalCode()) && !isBlank(address.postalCode())) {
            report.setPostalCode(address.postalCode());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static StreetParts parseStreet(String streetLine) {
        if (streetLine == null || streetLine.isBlank()) {
            return new StreetParts(null, null);
        }
        String trimmed = streetLine.trim();
        var matcher = STREET_WITH_NUMBER.matcher(trimmed);
        if (matcher.matches()) {
            String street = matcher.group(1).trim();
            String houseNumber = matcher.group(2).trim();
            if (!street.isBlank()) {
                return new StreetParts(street, houseNumber);
            }
        }
        return new StreetParts(trimmed, null);
    }
}
