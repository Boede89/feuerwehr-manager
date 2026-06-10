package de.feuerwehr.manager.berichte;

public record PersonDamageEntry(String name, String address, String birthdate) {

    public static PersonDamageEntry empty() {
        return new PersonDamageEntry(null, null, null);
    }
}
