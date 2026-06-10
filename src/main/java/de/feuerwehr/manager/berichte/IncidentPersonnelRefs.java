package de.feuerwehr.manager.berichte;

public final class IncidentPersonnelRefs {

    private IncidentPersonnelRefs() {}

    public static boolean isUcrRef(long refId) {
        return refId < 0;
    }

    public static long ucrFromRef(long refId) {
        if (!isUcrRef(refId)) {
            throw new IllegalArgumentException("Keine DIVERA-UCR-Referenz: " + refId);
        }
        return -refId;
    }

    public static long refFromUcr(long ucrId) {
        if (ucrId <= 0) {
            throw new IllegalArgumentException("Ungültige DIVERA-UCR-ID: " + ucrId);
        }
        return -ucrId;
    }

    public static String displayNameForUcr(long ucrId) {
        return "DIVERA UCR " + ucrId;
    }
}
