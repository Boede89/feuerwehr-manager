package de.feuerwehr.manager.dashboard;

/** Speicherziel für die Einheits-Startseitenvorlage. */
public enum UnitDashboardApplyMode {
    NEW_USERS_ONLY,
    ALL_USERS;

    public static UnitDashboardApplyMode fromRaw(Object raw) {
        if (raw == null) {
            return NEW_USERS_ONLY;
        }
        String value = String.valueOf(raw).trim().toUpperCase();
        if ("ALL".equals(value)
                || "ALL_USERS".equals(value)
                || "ALLE".equals(value)
                || "ALL_USERS_ONLY".equals(value)) {
            return ALL_USERS;
        }
        return NEW_USERS_ONLY;
    }
}
