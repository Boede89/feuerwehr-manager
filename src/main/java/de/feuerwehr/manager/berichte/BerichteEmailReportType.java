package de.feuerwehr.manager.berichte;

public enum BerichteEmailReportType {
    EINSATZ("einsatz", "Einsatzbericht", true),
    ANWESENHEIT("anwesenheit", "Anwesenheitsliste", true),
    GERAETEWART("geraetewart", "Gerätewartmitteilung", false),
    MAENGEL("maengel", "Mängelbericht", false),
    CHECKLISTEN("checklisten", "Checkliste", false);

    private final String tabKey;
    private final String label;
    private final boolean statusTrigger;

    BerichteEmailReportType(String tabKey, String label, boolean statusTrigger) {
        this.tabKey = tabKey;
        this.label = label;
        this.statusTrigger = statusTrigger;
    }

    public String tabKey() {
        return tabKey;
    }

    public String label() {
        return label;
    }

    public boolean statusTrigger() {
        return statusTrigger;
    }

    public static BerichteEmailReportType fromTab(String tabKey) {
        if (tabKey == null) {
            return EINSATZ;
        }
        for (BerichteEmailReportType type : values()) {
            if (type.tabKey.equals(tabKey)) {
                return type;
            }
        }
        return EINSATZ;
    }
}
