package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.unit.Unit;

public final class UnitPostalCity {

    private UnitPostalCity() {}

    public record Parts(String postalCode, String city) {}

    public static Parts parse(String postalCity) {
        if (postalCity == null || postalCity.isBlank()) {
            return new Parts(null, null);
        }
        String trimmed = postalCity.trim();
        if (trimmed.length() >= 6 && Character.isDigit(trimmed.charAt(0))) {
            int space = trimmed.indexOf(' ');
            if (space > 0) {
                String plz = trimmed.substring(0, space).trim();
                String city = trimmed.substring(space + 1).trim();
                if (plz.matches("\\d{4,5}") && !city.isBlank()) {
                    return new Parts(plz, city);
                }
            }
        }
        return new Parts(null, trimmed);
    }

    public static Parts fromUnit(Unit unit) {
        Parts parts = parse(unit.getPostalCity());
        if (parts.city() != null && !parts.city().isBlank()) {
            return parts;
        }
        if (unit.getName() != null && !unit.getName().isBlank()) {
            return new Parts(parts.postalCode(), unit.getName().trim());
        }
        return parts;
    }
}
