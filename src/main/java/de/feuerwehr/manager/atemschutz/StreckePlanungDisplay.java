package de.feuerwehr.manager.atemschutz;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Thymeleaf-freundliche Anzeige-Objekte (keine Records). */
public final class StreckePlanungDisplay {

    private StreckePlanungDisplay() {}

    @Getter
    @RequiredArgsConstructor
    public static class TerminCard {
        private final long id;
        private final String datumAnzeige;
        private final String datumIso;
        private final String zeitAnzeige;
        private final String zeitInput;
        private final String ort;
        private final String bemerkung;
        private final int maxTeilnehmer;
        private final int aktuelleTeilnehmer;
        private final boolean vergangen;
        private final boolean voll;
        private final String headerModifier;
        private final List<TeilnehmerBadge> teilnehmer;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TeilnehmerBadge {
        private final long carrierId;
        private final String name;
        private final String streckeBisIso;
        private final String streckeBisAnzeige;
        private final String metaText;
        private final String statusDot;
        private final boolean notified;
        private final boolean abgelaufen;
    }

    @Getter
    @RequiredArgsConstructor
    public static class PoolBadge {
        private final long carrierId;
        private final String name;
        private final String streckeBisIso;
        private final String metaText;
        private final String statusDot;
        private final boolean abgelaufen;
        private final boolean warnung;
    }
}
