package de.feuerwehr.manager.personal;

public enum EquipmentType {
    PAGER("Pager"),
    KEY("Schlüssel"),
    TRANSPONDER("Transponder"),
    ID_CARD("Dienstausweis"),
    DRIVING_PERMIT("Fahrberechtigung");

    private final String label;

    EquipmentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
