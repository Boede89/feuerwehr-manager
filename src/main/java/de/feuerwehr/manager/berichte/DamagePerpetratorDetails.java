package de.feuerwehr.manager.berichte;

public record DamagePerpetratorDetails(String name, String address, String birthdate, String licensePlate) {

    public static DamagePerpetratorDetails empty() {
        return new DamagePerpetratorDetails(null, null, null, null);
    }

    public boolean hasContent() {
        return isFilled(name) || isFilled(address) || isFilled(birthdate) || isFilled(licensePlate);
    }

    public DamagePerpetratorDetails normalized() {
        return new DamagePerpetratorDetails(
                trimToNull(name),
                trimToNull(address),
                trimToNull(birthdate),
                trimToNull(licensePlate));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
