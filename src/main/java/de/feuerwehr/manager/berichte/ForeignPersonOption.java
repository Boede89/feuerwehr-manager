package de.feuerwehr.manager.berichte;

public record ForeignPersonOption(
        long personId, String displayName, String qualTier, long unitId, String unitName) {}
