package de.feuerwehr.manager.technik;

public enum ChecklistResult {
    OK("ok"),
    MANGEL("mangel"),
    NICHT_GEPRUEFT("nicht_geprueft");

    private final String key;

    ChecklistResult(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ChecklistResult fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return NICHT_GEPRUEFT;
        }
        String k = raw.trim().toLowerCase();
        for (ChecklistResult r : values()) {
            if (r.key.equals(k)) {
                return r;
            }
        }
        return NICHT_GEPRUEFT;
    }
}
