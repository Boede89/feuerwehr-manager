package de.feuerwehr.manager.berichte;

public record MaterialDamageEntry(
        String mangelAn,
        String bezeichnung,
        Long vehicleId,
        String mangelBeschreibung,
        String ursache,
        String verbleib) {

    public boolean hasContent() {
        boolean hasText = (bezeichnung != null && !bezeichnung.isBlank())
                || (mangelBeschreibung != null && !mangelBeschreibung.isBlank())
                || (ursache != null && !ursache.isBlank())
                || (verbleib != null && !verbleib.isBlank());
        return hasText || (vehicleId != null && vehicleId > 0);
    }

    public MaterialDamageEntry normalized() {
        String type = mangelAn != null && !mangelAn.isBlank()
                ? MaengelberichtMangelAn.fromKey(mangelAn).name()
                : MaengelberichtMangelAn.GEBAEUDE.name();
        Long normalizedVehicleId = vehicleId != null && vehicleId > 0 ? vehicleId : null;
        return new MaterialDamageEntry(
                type,
                trimOrNull(bezeichnung),
                normalizedVehicleId,
                trimOrNull(mangelBeschreibung),
                trimOrNull(ursache),
                trimOrNull(verbleib));
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
