package de.feuerwehr.manager.unit;

public enum RolePermissionLevel {
    READ("read", "Lesen"),
    WRITE("write", "Schreiben"),
    APPROVE("approve", "Genehmigen");

    private final String suffix;
    private final String label;

    RolePermissionLevel(String suffix, String label) {
        this.suffix = suffix;
        this.label = label;
    }

    public String suffix() {
        return suffix;
    }

    public String label() {
        return label;
    }

    public String permissionKey(String moduleKey) {
        return moduleKey + "." + suffix;
    }
}
