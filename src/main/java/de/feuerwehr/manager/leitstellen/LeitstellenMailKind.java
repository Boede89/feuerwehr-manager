package de.feuerwehr.manager.leitstellen;

public enum LeitstellenMailKind {
    DEPESCHE("Depeche.pdf"),
    ABSCHLUSS("Abschlussbericht.pdf");

    private final String storedFilename;

    LeitstellenMailKind(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String storedFilename() {
        return storedFilename;
    }
}
