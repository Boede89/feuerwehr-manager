package de.feuerwehr.manager.atemschutz;

/** Eintragstyp im Self-Service-Antrag (UI); wird bei Freigabe auf Fitness-Typ gemappt. */
public enum AtemschutzEntryRequestType {
    EINSATZ,
    UEBUNG,
    STRECKE,
    G26,
    CSA;

    public String label() {
        return switch (this) {
            case EINSATZ -> "Einsatz";
            case UEBUNG -> "Übung";
            case STRECKE -> "Atemschutzstrecke";
            case G26 -> "G26.3";
            case CSA -> "CSA";
        };
    }

    public AtemschutzFitnessType toFitnessType() {
        return switch (this) {
            case EINSATZ, UEBUNG -> AtemschutzFitnessType.UEBUNG;
            case STRECKE -> AtemschutzFitnessType.STRECKEN;
            case G26 -> AtemschutzFitnessType.G26_UNTERSUCHUNG;
            case CSA -> AtemschutzFitnessType.CSA;
        };
    }

    public static AtemschutzEntryRequestType fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Eintragstyp fehlt.");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannter Eintragstyp: " + raw);
        }
    }
}
