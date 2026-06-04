package de.feuerwehr.manager.unit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Modul-Berechtigungen für Einheits-Rollen (angelehnt an FW-Hub). */
public final class UnitRolePermission {

    public static final String PERSONAL = "personal";
    public static final String RESERVIERUNGEN = "reservierungen";
    public static final String ATEMSCHUTZ = "atemschutz";
    public static final String BERICHTE = "berichte";
    public static final String AUSWERTUNG = "auswertung";
    public static final String TECHNIK = "technik";

    private static final Set<String> ALLOWED = Set.of(
            PERSONAL, RESERVIERUNGEN, ATEMSCHUTZ, BERICHTE, AUSWERTUNG, TECHNIK);

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(PERSONAL, "Personal");
        LABELS.put(RESERVIERUNGEN, "Reservierungen");
        LABELS.put(ATEMSCHUTZ, "Atemschutz");
        LABELS.put(BERICHTE, "Berichte");
        LABELS.put(AUSWERTUNG, "Auswertung");
        LABELS.put(TECHNIK, "Technik");
    }

    private UnitRolePermission() {}

    public static Map<String, String> labels() {
        return LABELS;
    }

    public static List<String> filterAllowed(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream().filter(ALLOWED::contains).distinct().toList();
    }
}
