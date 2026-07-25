package de.feuerwehr.manager.reservierungen;

/** Ausnahme bei Löschfahrzeug-Mindestverfügbarkeit — UI kann Override anbieten. */
public class LoeschfahrzeugWarningException extends IllegalArgumentException {

    private final LoeschfahrzeugWarningView warning;

    public LoeschfahrzeugWarningException(LoeschfahrzeugWarningView warning) {
        super(warning != null && warning.message() != null
                ? warning.message()
                : "Löschfahrzeug-Mindestverfügbarkeit unterschritten.");
        this.warning = warning;
    }

    public LoeschfahrzeugWarningView getWarning() {
        return warning;
    }
}
