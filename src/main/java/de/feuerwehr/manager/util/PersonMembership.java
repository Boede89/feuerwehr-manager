package de.feuerwehr.manager.util;

import de.feuerwehr.manager.personal.Person;
import java.time.LocalDate;

/**
 * Mitgliedschaft anhand Eintritts-/Austrittsdatum.
 * Ohne Datum: uneingeschränkt. Austritt gilt inklusiv (am Austrittstag noch Mitglied).
 */
public final class PersonMembership {

    private PersonMembership() {}

    /** Aktuelles Mitglied (heute innerhalb Eintritt–Austritt). */
    public static boolean isCurrentlyMember(Person person) {
        return isMemberOn(person, LocalDate.now());
    }

    /** Mitgliedschaft am angegebenen Tag. */
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
