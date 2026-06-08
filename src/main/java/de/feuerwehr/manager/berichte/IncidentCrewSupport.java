package de.feuerwehr.manager.berichte;

/** Hilfskonstanten für Personal-/Fahrzeug-Zuordnung im Einsatzbericht. */
public final class IncidentCrewSupport {

    /** Personal an der Einsatzstelle ohne konkretes Fahrzeug. */
    public static final long EINSATZSTELLE_VEHICLE_ID = -2L;
    public static final String EINSATZSTELLE_VEHICLE_NAME = "Einsatzstelle";

    /** Personal nur an der Wache (nicht mit Fahrzeug ausgerückt). */
    public static final long WACHE_VEHICLE_ID = -1L;
    public static final String WACHE_VEHICLE_NAME = "Wache";

    private IncidentCrewSupport() {}

    public static boolean isVirtualSlot(long vehicleId) {
        return vehicleId == EINSATZSTELLE_VEHICLE_ID || vehicleId == WACHE_VEHICLE_ID;
    }

    public static String virtualSlotName(long vehicleId) {
        if (vehicleId == EINSATZSTELLE_VEHICLE_ID) {
            return EINSATZSTELLE_VEHICLE_NAME;
        }
        if (vehicleId == WACHE_VEHICLE_ID) {
            return WACHE_VEHICLE_NAME;
        }
        return null;
    }
}
