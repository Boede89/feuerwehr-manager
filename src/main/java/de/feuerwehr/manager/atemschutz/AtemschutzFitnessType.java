package de.feuerwehr.manager.atemschutz;

public enum AtemschutzFitnessType {
    G26_UNTERSUCHUNG,
    UEBUNG,
    STRECKEN;

    public String label() {
        return switch (this) {
            case G26_UNTERSUCHUNG -> "G26-Untersuchung";
            case UEBUNG -> "Übungsnachweis";
            case STRECKEN -> "Strecke";
        };
    }

    public boolean healthData() {
        return this == G26_UNTERSUCHUNG;
    }
}
