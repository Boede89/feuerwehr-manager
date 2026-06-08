package de.feuerwehr.manager.berichte;

/** Hilfskonstanten für Personal-/Fahrzeug-Zuordnung im Einsatzbericht. */
public final class IncidentCrewSupport {

    /** Personal an der Einsatzstelle ohne konkretes Fahrzeug. */
    public static final long EINSATZSTELLE_VEHICLE_ID = -2L;
    public static final String EINSATZSTELLE_VEHICLE_NAME = "Einsatzstelle";

    /** @deprecated Frühere Bezeichnung; wird beim Laden noch erkannt. */
    public static final long WACHE_VEHICLE_ID = -1L;
    public static final String WACHE_VEHICLE_NAME = "Wache";

    private IncidentCrewSupport() {}

    public static boolean isSceneWithoutVehicle(long vehicleId) {
        return vehicleId == EINSATZSTELLE_VEHICLE_ID || vehicleId == WACHE_VEHICLE_ID;
    }

    public static boolean isSceneWithoutVehicleName(String vehicleName) {
        return EINSATZSTELLE_VEHICLE_NAME.equals(vehicleName) || WACHE_VEHICLE_NAME.equals(vehicleName);
    }
}
