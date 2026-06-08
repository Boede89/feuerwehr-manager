package de.feuerwehr.manager.berichte;

public enum BerichteTab {
    EINSATZ("einsatz", "Einsatzberichte"),
    ANWESENHEIT("anwesenheit", "Anwesenheitslisten"),
    GERAETEWART("geraetewart", "Gerätewartmitteilungen"),
    MAENGEL("maengel", "Mängelberichte"),
    CHECKLISTEN("checklisten", "Checklisten");

    private final String key;
    private final String label;

    BerichteTab(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static BerichteTab fromKey(String key) {
        if (key == null) {
            return EINSATZ;
        }
        for (BerichteTab tab : values()) {
            if (tab.key.equals(key)) {
                return tab;
            }
        }
        return EINSATZ;
    }
}
