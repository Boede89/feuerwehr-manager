package de.feuerwehr.manager.divera;

public record DiveraImportResult(boolean success, String message, int imported, int skipped) {

    public static DiveraImportResult ok(int imported, int skipped, String message) {
        return new DiveraImportResult(true, message, imported, skipped);
    }

    public static DiveraImportResult fail(String message) {
        return new DiveraImportResult(false, message, 0, 0);
    }
}
