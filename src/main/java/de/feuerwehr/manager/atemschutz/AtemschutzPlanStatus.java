package de.feuerwehr.manager.atemschutz;

import java.util.Locale;
import java.util.Map;

public enum AtemschutzPlanStatus {
    TAUGLICH,
    WARNUNG,
    ABGELAUFEN,
    UEBUNG_ABGELAUFEN;

    public String label() {
        return switch (this) {
            case TAUGLICH -> "Tauglich";
            case WARNUNG -> "Warnung";
            case ABGELAUFEN -> "Abgelaufen";
            case UEBUNG_ABGELAUFEN -> "Übung abgelaufen";
        };
    }

    public String badgeClass() {
        return switch (this) {
            case TAUGLICH -> "badge active";
            case WARNUNG -> "badge warn";
            case ABGELAUFEN, UEBUNG_ABGELAUFEN -> "badge inactive";
        };
    }

    public String filterChipClass() {
        return "atemschutz-plan-status-chip atemschutz-plan-status-chip--"
                + name().toLowerCase(Locale.ROOT);
    }

    public static AtemschutzPlanStatus fromParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static final Map<AtemschutzPlanStatus, Boolean> DEFAULT_SELECTED = Map.of(
            TAUGLICH, true,
            WARNUNG, true,
            ABGELAUFEN, false,
            UEBUNG_ABGELAUFEN, true);
}
