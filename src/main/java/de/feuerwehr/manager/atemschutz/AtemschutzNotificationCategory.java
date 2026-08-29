package de.feuerwehr.manager.atemschutz;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AtemschutzNotificationCategory {
    G26(
            "G26.3",
            AtemschutzFitnessType.G26_UNTERSUCHUNG,
            "g263_warnung",
            "g263_abgelaufen",
            "g26WarnDays",
            "g26NotifyInstructors",
            "g26NotifyCarriers",
            "g26CcPersonIds"),
    STRECKEN(
            "Strecke",
            AtemschutzFitnessType.STRECKEN,
            "strecke_warnung",
            "strecke_abgelaufen",
            "streckeWarnDays",
            "streckeNotifyInstructors",
            "streckeNotifyCarriers",
            "streckeCcPersonIds"),
    UEBUNG(
            "Übung / Einsatz",
            AtemschutzFitnessType.UEBUNG,
            "uebung_warnung",
            "uebung_abgelaufen",
            "uebungWarnDays",
            "uebungNotifyInstructors",
            "uebungNotifyCarriers",
            "uebungCcPersonIds"),
    CSA(
            "CSA",
            AtemschutzFitnessType.CSA,
            "csa_warnung",
            "csa_abgelaufen",
            "csaWarnDays",
            "csaNotifyInstructors",
            "csaNotifyCarriers",
            "csaCcPersonIds");

    private final String label;
    private final AtemschutzFitnessType fitnessType;
    private final String warnungTemplateKey;
    private final String abgelaufenTemplateKey;
    private final String warnDaysField;
    private final String notifyInstructorsField;
    private final String notifyCarriersField;
    private final String ccPersonIdsField;

    public static AtemschutzNotificationCategory fromFitnessType(AtemschutzFitnessType type) {
        if (type == null) {
            throw new IllegalArgumentException("Nachweistyp fehlt.");
        }
        for (AtemschutzNotificationCategory category : values()) {
            if (category.fitnessType == type) {
                return category;
            }
        }
        throw new IllegalArgumentException("Kein Benachrichtigungstyp für " + type.label() + ".");
    }
}
