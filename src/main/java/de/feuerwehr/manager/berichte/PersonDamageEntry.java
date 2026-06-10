package de.feuerwehr.manager.berichte;

import java.time.LocalDate;

public record PersonDamageEntry(String name, String address, LocalDate birthdate) {

    public static PersonDamageEntry empty() {
        return new PersonDamageEntry(null, null, null);
    }
}
