package de.feuerwehr.manager.berichte;

public enum IncidentReportStatus {
    ENTWURF,
    FREIGEGEBEN,
    ARCHIVIERT;

    public String label() {
        return switch (this) {
            case ENTWURF -> "Entwurf";
            case FREIGEGEBEN -> "Freigegeben";
            case ARCHIVIERT -> "Archiviert";
        };
    }
}
