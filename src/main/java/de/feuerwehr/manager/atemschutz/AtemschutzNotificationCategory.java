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
            "g26CcUserIds"),
    STRECKEN(
            "Strecke",
            AtemschutzFitnessType.STRECKEN,
            "strecke_warnung",
            "strecke_abgelaufen",
            "streckeWarnDays",
            "streckeNotifyInstructors",
            "streckeCcUserIds"),
    UEBUNG(
            "Übung / Einsatz",
            AtemschutzFitnessType.UEBUNG,
            "uebung_warnung",
            "uebung_abgelaufen",
            "uebungWarnDays",
            "uebungNotifyInstructors",
            "uebungCcUserIds");

    private final String label;
    private final AtemschutzFitnessType fitnessType;
    private final String warnungTemplateKey;
    private final String abgelaufenTemplateKey;
    private final String warnDaysField;
    private final String notifyInstructorsField;
    private final String ccUserIdsField;
}
