package de.feuerwehr.manager.divera;

import java.util.Map;

/** DIVERA-Benutzerstamm aus {@code GET /api/users}. */
public record DiveraUserDirectory(Map<Long, Integer> statusByUcr, Map<Long, String> displayNameByUcr) {

    public static DiveraUserDirectory empty() {
        return new DiveraUserDirectory(Map.of(), Map.of());
    }
}
