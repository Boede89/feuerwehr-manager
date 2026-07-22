package de.feuerwehr.manager.berichte;

/**
 * Personenschaden an einer Einsatzkraft (z. B. für späteres Verbandsbuch).
 */
public record CrewInjuryEntry(Long personId, String personName, String time, String description) {}
