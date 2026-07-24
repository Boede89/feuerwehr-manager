package de.feuerwehr.manager.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Gemeinsame Hilfen für Jahresfilter (nur Jahre mit Daten, absteigend). */
public final class YearFilterSupport {

    private YearFilterSupport() {}

    /**
     * Liefert absteigend sortierte, eindeutige Jahre. Ist die Menge leer, wird das aktuelle Jahr
     * ergänzt (leerer Filterzustand).
     */
    public static List<Integer> descendingYears(Collection<Integer> years) {
        Set<Integer> sorted = new TreeSet<>(Comparator.reverseOrder());
        if (years != null) {
            for (Integer year : years) {
                if (year != null && year >= 1900 && year <= 2100) {
                    sorted.add(year);
                }
            }
        }
        if (sorted.isEmpty()) {
            sorted.add(LocalDate.now().getYear());
        }
        return List.copyOf(sorted);
    }

    /** Vereinigt mehrere Jahreslisten und liefert {@link #descendingYears(Collection)}. */
    @SafeVarargs
    public static List<Integer> mergeDescending(Collection<Integer>... groups) {
        LinkedHashSet<Integer> merged = new LinkedHashSet<>();
        if (groups != null) {
            for (Collection<Integer> group : groups) {
                if (group == null) {
                    continue;
                }
                for (Integer year : group) {
                    if (year != null) {
                        merged.add(year);
                    }
                }
            }
        }
        return descendingYears(merged);
    }

    /**
     * Wählt das angeforderte Jahr, falls in den Optionen enthalten; sonst das neueste verfügbare
     * Jahr.
     */
    public static int resolveSelected(Integer requested, List<Integer> options) {
        List<Integer> years = options == null || options.isEmpty()
                ? List.of(LocalDate.now().getYear())
                : options;
        if (requested != null && years.contains(requested)) {
            return requested;
        }
        return years.get(0);
    }

    /** True, wenn {@code eventDate} am oder nach dem Eintrittsdatum liegt (null = immer true). */
    public static boolean isOnOrAfterEntry(LocalDate eventDate, LocalDate entryDate) {
        if (entryDate == null) {
            return true;
        }
        if (eventDate == null) {
            return false;
        }
        return !eventDate.isBefore(entryDate);
    }

    /** True, wenn {@code eventDate} am oder vor dem Austrittsdatum liegt (null = immer true). */
    public static boolean isOnOrBeforeExit(LocalDate eventDate, LocalDate exitDate) {
        if (exitDate == null) {
            return true;
        }
        if (eventDate == null) {
            return false;
        }
        return !eventDate.isAfter(exitDate);
    }

    /** Termin liegt in der Mitgliedschaft (Eintritt inklusiv, Austritt inklusiv). */
    public static boolean isWithinMembership(LocalDate eventDate, LocalDate entryDate, LocalDate exitDate) {
        return isOnOrAfterEntry(eventDate, entryDate) && isOnOrBeforeExit(eventDate, exitDate);
    }

    public static List<Integer> asMutableList(List<Integer> years) {
        return new ArrayList<>(Objects.requireNonNullElse(years, List.of()));
    }
}
