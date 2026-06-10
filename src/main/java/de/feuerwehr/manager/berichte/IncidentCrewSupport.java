package de.feuerwehr.manager.berichte;

/** Hilfskonstanten für Personal-/Fahrzeug-Zuordnung im Einsatzbericht. */
public final class IncidentCrewSupport {

    /** Personal an der Einsatzstelle ohne konkretes Fahrzeug. */
    public static final long EINSATZSTELLE_VEHICLE_ID = -2L;
    public static final String EINSATZSTELLE_VEHICLE_NAME = "Einsatzstelle";

    /** Personal nur an der Wache (nicht mit Fahrzeug ausgerückt). */
    public static final long WACHE_VEHICLE_ID = -1L;
    public static final String WACHE_VEHICLE_NAME = "Wache";

    /** Personal am Einsatz beteiligt, noch keinem Fahrzeug zugeordnet. */
    public static final long BETEILIGT_VEHICLE_ID = -3L;
    public static final String BETEILIGT_VEHICLE_NAME = "Am Einsatz beteiligt";

    private IncidentCrewSupport() {}

    public static boolean isVirtualSlot(long vehicleId) {
        return vehicleId == EINSATZSTELLE_VEHICLE_ID
                || vehicleId == WACHE_VEHICLE_ID
                || vehicleId == BETEILIGT_VEHICLE_ID;
    }

    public static String virtualSlotName(long vehicleId) {
        if (vehicleId == EINSATZSTELLE_VEHICLE_ID) {
            return EINSATZSTELLE_VEHICLE_NAME;
        }
        if (vehicleId == WACHE_VEHICLE_ID) {
            return WACHE_VEHICLE_NAME;
        }
        if (vehicleId == BETEILIGT_VEHICLE_ID) {
            return BETEILIGT_VEHICLE_NAME;
        }
        return null;
    }
}
