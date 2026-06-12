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

    public static void applyDefaultsToForm(EinsatzberichtForm form, Unit unit) {
        applyDefaultsToFormIfBlank(form, unit);
    }

    public static void applyDefaultsToFormIfBlank(EinsatzberichtForm form, Unit unit) {
        if (form == null || unit == null) {
            return;
        }
        UnitAddress address = fromUnit(unit);
        if (isBlank(form.getPostalCode()) && !isBlank(address.postalCode())) {
            form.setPostalCode(address.postalCode());
        }
        if (isBlank(form.getLocation()) || "—".equals(form.getLocation().trim())) {
            if (!isBlank(address.location())) {
                form.setLocation(address.location());
            }
        }
        if (isBlank(form.getStreet()) && !isBlank(address.street())) {
            form.setStreet(address.street());
        }
        if (isBlank(form.getHouseNumber()) && !isBlank(address.houseNumber())) {
            form.setHouseNumber(address.houseNumber());
        }
    }

    public static void applyDefaultsToReportIfBlank(AttendanceReport report, Unit unit) {
        if (report == null || unit == null) {
            return;
        }
        UnitAddress address = fromUnit(unit);
        if (isBlank(report.getPostalCode()) && !isBlank(address.postalCode())) {
            report.setPostalCode(address.postalCode());
        }
        if (isBlank(report.getLocation()) || "—".equals(report.getLocation().trim())) {
            if (!isBlank(address.location())) {
                report.setLocation(address.location());
            }
        }
        if (isBlank(report.getStreet()) && !isBlank(address.street())) {
            report.setStreet(address.street());
        }
        if (isBlank(report.getHouseNumber()) && !isBlank(address.houseNumber())) {
            report.setHouseNumber(address.houseNumber());
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
