package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.IncidentReport;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest Meldender und Telefonnummer aus durchsuchbaren Leitstellen-PDFs
 * (Depeche / Abschlussbericht). Typisches Format: {@code Meldender Järchel/004915221009673}.
 */
public final class LeitstellenPdfReporterSupport {

    public record ReporterContact(String name, String phone) {}

    private static final Pattern MELDENDER_LINE = Pattern.compile("(?i)^(.*)\\bMeldender\\b\\s*[:.]?\\s*(.*)$");

    private static final Pattern NAME_SLASH_PHONE = Pattern.compile(
            "^(.+?)\\s*/\\s*((?:00|\\+|0)[\\d\\s()./-]*\\d)");

    private static final Pattern NAME_THEN_PHONE = Pattern.compile(
            "^(.+?)\\s+((?:00|\\+|0)[\\d\\s()./-]*\\d)");

    private static final Pattern PHONE_ONLY = Pattern.compile("^((?:00|\\+|0)[\\d\\s()./-]*\\d)");

    private LeitstellenPdfReporterSupport() {}

    public static Optional<ReporterContact> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] lines = text.replace('\u00a0', ' ').split("\\R");
        Optional<ReporterContact> nameOnly = Optional.empty();
        for (int i = 0; i < lines.length; i++) {
            Matcher header = MELDENDER_LINE.matcher(lines[i].trim());
            if (!header.matches()) {
                continue;
            }
            Optional<ReporterContact> sameLine = parseNameAndPhone(header.group(2));
            if (hasPhone(sameLine)) {
                return sameLine;
            }
            for (int j = i + 1; j < Math.min(lines.length, i + 5); j++) {
                String next = collapseWhitespace(lines[j]);
                if (next.isEmpty() || isStopLabelLine(next)) {
                    continue;
                }
                Optional<ReporterContact> fromNext = parseNameAndPhone(next);
                if (hasPhone(fromNext)) {
                    return fromNext;
                }
                if (nameOnly.isEmpty() && fromNext.isPresent()) {
                    nameOnly = fromNext;
                }
            }
            if (nameOnly.isEmpty() && sameLine.isPresent()) {
                nameOnly = sameLine;
            }
        }
        return nameOnly;
    }

    public static Optional<ReporterContact> parseNameAndPhone(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = collapseWhitespace(value);
        Optional<ReporterContact> slash = matchNamePhone(NAME_SLASH_PHONE, trimmed);
        if (slash.isPresent()) {
            return slash;
        }
        Optional<ReporterContact> spaced = matchNamePhone(NAME_THEN_PHONE, trimmed);
        if (spaced.isPresent()) {
            return spaced;
        }
        Matcher phoneOnly = PHONE_ONLY.matcher(trimmed);
        if (phoneOnly.find()) {
            String phone = normalizePhone(phoneOnly.group(1));
            if (isPlausiblePhone(phone) && phoneOnly.start() == 0 && phoneOnly.end() >= trimmed.length()) {
                return Optional.of(new ReporterContact(null, clip(phone, 64)));
            }
        }
        if (isPlausibleName(trimmed) && !looksLikePhone(trimmed)) {
            return Optional.of(new ReporterContact(clip(trimmed, 255), null));
        }
        return Optional.empty();
    }

    public static boolean applyIfMissing(IncidentReport report, ReporterContact contact) {
        if (report == null || contact == null) {
            return false;
        }
        boolean changed = false;
        if (isBlank(report.getReporterName()) && hasText(contact.name())) {
            report.setReporterName(contact.name());
            changed = true;
        }
        if (isBlank(report.getReporterPhone()) && hasText(contact.phone())) {
            report.setReporterPhone(contact.phone());
            changed = true;
        }
        return changed;
    }

    public static boolean splitCombinedReporterNameIfNeeded(IncidentReport report) {
        if (report == null || !isBlank(report.getReporterPhone()) || isBlank(report.getReporterName())) {
            return false;
        }
        Optional<ReporterContact> parsed = parseNameAndPhone(report.getReporterName());
        if (parsed.isEmpty() || !hasText(parsed.get().phone()) || !hasText(parsed.get().name())) {
            return false;
        }
        report.setReporterName(parsed.get().name());
        report.setReporterPhone(parsed.get().phone());
        return true;
    }

    private static Optional<ReporterContact> matchNamePhone(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String name = collapseWhitespace(matcher.group(1));
        String phone = normalizePhone(matcher.group(2));
        if (!isPlausibleName(name) || !isPlausiblePhone(phone)) {
            return Optional.empty();
        }
        return Optional.of(new ReporterContact(clip(name, 255), clip(phone, 64)));
    }

    private static boolean isStopLabelLine(String line) {
        String lower = line.toLowerCase(Locale.GERMAN);
        return lower.matches(
                "^(meldeweg|stichwort|objekt|ortsteil|einsatzort|meldebild|bemerkung|stra(?:ss|ß)e|ort)\\b.*");
    }

    private static boolean hasPhone(Optional<ReporterContact> contact) {
        return contact.isPresent() && hasText(contact.get().phone());
    }

    private static String collapseWhitespace(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.replace('\u00a0', ' ').replaceAll("[\\s()./-]", "").trim();
        if (compact.startsWith("00") || compact.startsWith("+") || compact.startsWith("0")) {
            return compact;
        }
        return compact;
    }

    private static boolean isPlausibleName(String name) {
        if (!hasText(name) || name.length() > 255) {
            return false;
        }
        String lower = name.toLowerCase(Locale.GERMAN);
        if (lower.equals("-")
                || lower.equals("—")
                || lower.equals("unbekannt")
                || lower.equals("k.a.")
                || lower.equals("k. a.")
                || isStopLabelLine(name)) {
            return false;
        }
        return name.chars().anyMatch(Character::isLetter);
    }

    private static boolean isPlausiblePhone(String phone) {
        if (!hasText(phone)) {
            return false;
        }
        String digits = phone.replace("+", "");
        if (digits.length() < 6 || digits.length() > 20) {
            return false;
        }
        return digits.chars().allMatch(Character::isDigit);
    }

    private static boolean looksLikePhone(String value) {
        String compact = value.replaceAll("[\\s()./-]", "");
        return compact.matches("^(00|\\+|0)\\d{6,}$");
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
