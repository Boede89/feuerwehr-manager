package de.feuerwehr.manager.berichte;

public record DamagePerpetratorDetails(String name, String address, String birthdate, String licensePlate) {

    public static DamagePerpetratorDetails empty() {
        return new DamagePerpetratorDetails(null, null, null, null);
    }

    public boolean hasContent() {
        return isFilled(name) || isFilled(address) || isFilled(birthdate) || isFilled(licensePlate);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
