package de.feuerwehr.manager.berichte;

import java.time.LocalDate;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hilfslogik für Einsatznummern (Format: YYYY-MM-DD-NN, Zähler pro Jahr). */
public final class IncidentNumberSupport {

    private static final Pattern INCIDENT_NUMBER_PATTERN =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})-(\\d+)$");

    private IncidentNumberSupport() {}

    public static String suggestForDate(LocalDate date, Collection<String> existingNumbers) {
        if (date == null) {
            date = LocalDate.now();
        }
        int year = date.getYear();
        int next = maxSequenceForYear(year, existingNumbers) + 1;
        return date + "-" + formatSequence(next);
    }

    public static int maxSequenceForYear(int year, Collection<String> existingNumbers) {
        if (existingNumbers == null || existingNumbers.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (String number : existingNumbers) {
            ParsedIncidentNumber parsed = parse(number);
            if (parsed != null && parsed.year() == year && parsed.sequence() > max) {
                max = parsed.sequence();
            }
        }
        return max;
    }

    public static String formatSequence(int sequence) {
        if (sequence < 100) {
            return String.format("%02d", sequence);
        }
        return String.valueOf(sequence);
    }

    public static ParsedIncidentNumber parse(String incidentNumber) {
        if (incidentNumber == null || incidentNumber.isBlank()) {
            return null;
        }
        Matcher matcher = INCIDENT_NUMBER_PATTERN.matcher(incidentNumber.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int sequence = Integer.parseInt(matcher.group(4));
            return new ParsedIncidentNumber(year, month, day, sequence);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ParsedIncidentNumber(int year, int month, int day, int sequence) {}
}
