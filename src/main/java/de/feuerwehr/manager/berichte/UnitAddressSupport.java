package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.unit.Unit;
import java.util.regex.Pattern;

public final class UnitAddressSupport {

    private static final Pattern STREET_WITH_NUMBER = Pattern.compile("^(.*?)(?:\\s+(\\d+\\s*[a-zA-Z]?))$");

    private UnitAddressSupport() {}

    public record UnitAddress(String location, String postalCode, String street, String houseNumber) {}

    public record StreetParts(String street, String houseNumber) {}

    public static UnitAddress fromUnit(Unit unit) {
        if (unit == null) {
            return new UnitAddress(null, null, null, null);
        }
        UnitPostalCity.Parts postal = UnitPostalCity.fromUnit(unit);
        StreetParts streetParts = parseStreet(unit.getStreet());
        return new UnitAddress(postal.city(), postal.postalCode(), streetParts.street(), streetParts.houseNumber());
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
