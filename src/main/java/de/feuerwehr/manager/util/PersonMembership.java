package de.feuerwehr.manager.util;

import de.feuerwehr.manager.personal.Person;
import java.time.LocalDate;

/**
 * Mitgliedschaft anhand Eintritts-/Austrittsdatum.
 * Archiv: sobald ein Austrittsdatum gesetzt ist. Historische Auswertung: Austritt inklusiv.
 */
public final class PersonMembership {

    private PersonMembership() {}

    /** Im Archiv (Austrittsdatum hinterlegt). */
    public static boolean isArchived(Person person) {
        return person != null && person.getExitDate() != null;
    }

    /** Aktives Mitglied der Einheit (kein Austrittsdatum, Eintritt nicht in der Zukunft). */
    public static boolean isCurrentlyMember(Person person) {
        if (person == null || isArchived(person)) {
            return false;
        }
        LocalDate entry = person.getEntryDate();
        return entry == null || !LocalDate.now().isBefore(entry);
    }

    /** Mitgliedschaft am angegebenen Tag (für Historie; Austritt inklusiv). */
    public static boolean isMemberOn(Person person, LocalDate date) {
        if (person == null || date == null) {
            return false;
        }
        LocalDate entry = person.getEntryDate();
        if (entry != null && date.isBefore(entry)) {
            return false;
        }
        LocalDate exit = person.getExitDate();
        if (exit != null && date.isAfter(exit)) {
            return false;
        }
        return true;
    }

    /**
     * Überschneidung mit Kalenderjahr (für Auswertungs-Personenlisten).
     * Ohne Eintritt/Austritt: immer true.
     */
    public static boolean wasMemberDuringYear(Person person, int year) {
        if (person == null) {
            return false;
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate entry = person.getEntryDate();
        if (entry != null && entry.isAfter(yearEnd)) {
            return false;
        }
        LocalDate exit = person.getExitDate();
        if (exit != null && exit.isBefore(yearStart)) {
            return false;
        }
        return true;
    }
}
