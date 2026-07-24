package de.feuerwehr.manager.auswertung;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Eine Teilnahme an einem Dienst oder Einsatz (Personen-Modal). */
public record AuswertungPersonTeilnahme(
        String date,
        String label,
        @JsonProperty("pa") boolean pa) {}
