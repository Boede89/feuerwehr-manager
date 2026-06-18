package de.feuerwehr.manager.print;

public enum PrintMode {
    DIALOG("Druckdialog (Browser)"),
    CUPS("CUPS-Drucker (lp)");

    private final String label;

    PrintMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
