package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.berichte.IncidentReportTimeSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Zuordnung Leitstellen-FAX → Einsatzbericht.
 * Typ nach Stichworten bzw. Nähe zu Alarmbeginn / Einsatzende.
 * Liegt die Depeche schon vor, zählt eine spätere Mail (v. a. nach Ende / am Folgetag) als Abschluss —
 * auch genau um 0:00 Uhr.
 */
@Component
public class LeitstellenMailMatcher {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /** Mindestfenster nach Einsatzende für Abschlussbericht (inkl. Folgetag). */
    private static final int ABSCHLUSS_MIN_WINDOW_HOURS = 36;

    public record MatchResult(IncidentReport report, LeitstellenMailKind kind, int score) {}

    public Optional<MatchResult> match(
            UnitLeitstellenMailSettings settings,
            LeitstellenImapClient.MailMessage mail,
            LeitstellenImapClient.PdfAttachment pdf,
            List<IncidentReport> candidates) {
        return match(settings, mail, pdf, candidates, id -> false, id -> false);
    }

    public Optional<MatchResult> match(
            UnitLeitstellenMailSettings settings,
            LeitstellenImapClient.MailMessage mail,
            LeitstellenImapClient.PdfAttachment pdf,
            List<IncidentReport> candidates,
            Function<Long, Boolean> hasDepesche,
            Function<Long, Boolean> hasAbschluss) {
        if (candidates == null || candidates.isEmpty() || mail.receivedAt() == null) {
            return Optional.empty();
        }
        String haystack = normalize(
                nullToEmpty(mail.subject())
                        + " "
                        + nullToEmpty(mail.fromAddress())
                        + " "
                        + nullToEmpty(pdf.filename()));
        boolean faxStyle = haystack.contains("fax");
        int baseWindowHours = Math.max(1, settings.getMatchWindowHours());
        int abschlussWindowHours = Math.max(
                ABSCHLUSS_MIN_WINDOW_HOURS, (int) Math.ceil(baseWindowHours * 1.5));

        List<Scored> scored = new ArrayList<>();
        for (IncidentReport report : candidates) {
            boolean depescheDone = Boolean.TRUE.equals(hasDepesche.apply(report.getId()));
            boolean abschlussDone = Boolean.TRUE.equals(hasAbschluss.apply(report.getId()));

            LeitstellenMailKind kind = decideKind(
                    settings, haystack, report, mail, depescheDone, abschlussDone);
            int score = scoreForKind(report, mail, haystack, kind, baseWindowHours, abschlussWindowHours, faxStyle);
            if (score <= 0) {
                continue;
            }

            boolean alreadyHasKind =
                    (kind == LeitstellenMailKind.DEPESCHE && depescheDone)
                            || (kind == LeitstellenMailKind.ABSCHLUSS && abschlussDone);
            if (depescheDone && abschlussDone) {
                scored.add(new Scored(report, kind, score, true));
                continue;
            }
            if (alreadyHasKind) {
                // Mail gehört zu diesem Einsatz, Datei dieses Typs ist schon da → nicht erneut anhängen
                scored.add(new Scored(report, kind, score, true));
                continue;
            }
            scored.add(new Scored(report, kind, score, false));
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingInt(Scored::score)
                .reversed()
                .thenComparing(s -> bestTimeDistanceMinutes(s.report(), mail, s.kind()), Comparator.naturalOrder()));

        Scored best = scored.get(0);
        if (best.ownershipOnly()) {
            return Optional.empty();
        }
        int minScore = faxStyle ? 10 : 15;
        if (best.score() < minScore) {
            return Optional.empty();
        }
        if (scored.size() > 1) {
            Scored second = scored.get(1);
            if (second.score() == best.score()
                    && best.score() < 80
                    && bestTimeDistanceMinutes(best.report(), mail, best.kind())
                            == bestTimeDistanceMinutes(second.report(), mail, second.kind())) {
                return Optional.empty();
            }
        }
        return Optional.of(new MatchResult(best.report(), best.kind(), best.score()));
    }

    /**
     * Typ nach Stichworten, Einsatzende und Mail-Zeit.
     * Wichtig: Mail nach Ende bzw. am Folgetag / zweite Mail wenn Depeche schon da → Abschluss
     * (auch genau 00:00 Uhr).
     */
    private LeitstellenMailKind decideKind(
            UnitLeitstellenMailSettings settings,
            String haystack,
            IncidentReport report,
            LeitstellenImapClient.MailMessage mail,
            boolean depescheDone,
            boolean abschlussDone) {
        if (containsAnyKeyword(haystack, settings.getAbschlussKeywords())
                && !containsAnyKeyword(haystack, settings.getDepescheKeywords())) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        if (containsAnyKeyword(haystack, settings.getDepescheKeywords())
                && !containsAnyKeyword(haystack, settings.getAbschlussKeywords())) {
            return LeitstellenMailKind.DEPESCHE;
        }

        Instant alarm = alarmInstant(report);
        Instant end = endInstant(report);
        Instant received = mail.receivedAt();

        // Nach (oder knapp vor) Einsatzende → immer Abschluss, auch 00:00
        if (end != null && received != null && !received.isBefore(end.minus(Duration.ofMinutes(5)))) {
            return LeitstellenMailKind.ABSCHLUSS;
        }

        // Depeche schon da: spätere FAX-Mail ist der Abschluss (nicht als 2. Depeche verwerfen)
        if (depescheDone && !abschlussDone && received != null) {
            if (isNextCalendarDay(report, received)) {
                return LeitstellenMailKind.ABSCHLUSS;
            }
            if (alarm != null && !received.isBefore(alarm) && Duration.between(alarm, received).toMinutes() >= 15) {
                return LeitstellenMailKind.ABSCHLUSS;
            }
        }

        if (alarm != null && end != null && received != null) {
            long toAlarm = Math.abs(Duration.between(alarm, received).toMinutes());
            long toEnd = Math.abs(Duration.between(end, received).toMinutes());
            if (toEnd <= toAlarm) {
                return LeitstellenMailKind.ABSCHLUSS;
            }
            return LeitstellenMailKind.DEPESCHE;
        }
        // Kein Ende gesetzt, Mail am Folgetag → Abschluss
        if (end == null && received != null && isNextCalendarDay(report, received)) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        return LeitstellenMailKind.DEPESCHE;
    }

    private static boolean isNextCalendarDay(IncidentReport report, Instant received) {
        if (report.getIncidentDate() == null || received == null) {
            return false;
        }
        LocalDate mailDate = received.atZone(ZONE).toLocalDate();
        return mailDate.isAfter(report.getIncidentDate());
    }

    private static int scoreForKind(
            IncidentReport report,
            LeitstellenImapClient.MailMessage mail,
            String haystack,
            LeitstellenMailKind kind,
            int depescheWindowHours,
            int abschlussWindowHours,
            boolean faxStyle) {
        int score = textBonus(report, haystack);
        Instant anchor = kind == LeitstellenMailKind.ABSCHLUSS ? endInstant(report) : alarmInstant(report);
        if (anchor == null && kind == LeitstellenMailKind.ABSCHLUSS) {
            anchor = alarmInstant(report);
        }
        if (anchor == null || mail.receivedAt() == null) {
            if (report.getIncidentDate() != null && mail.receivedAt() != null) {
                LocalDate mailDate = mail.receivedAt().atZone(ZONE).toLocalDate();
                long days = Math.abs(Duration.between(
                                report.getIncidentDate().atStartOfDay(ZONE).toInstant(),
                                mailDate.atStartOfDay(ZONE).toInstant())
                        .toDays());
                int windowDays = Math.max(1, (kind == LeitstellenMailKind.ABSCHLUSS
                                        ? abschlussWindowHours
                                        : depescheWindowHours)
                                / 24
                        + 1);
                if (days <= windowDays) {
                    score += 12;
                    if (faxStyle) {
                        score += 5;
                    }
                    return score;
                }
            }
            return 0;
        }

        long minutes = Duration.between(anchor, mail.receivedAt()).toMinutes();
        long windowMinutes = (kind == LeitstellenMailKind.ABSCHLUSS ? abschlussWindowHours : depescheWindowHours)
                * 60L;
        if (kind == LeitstellenMailKind.ABSCHLUSS) {
            // Abschluss kommt nach dem Ende — früh vor dem Ende ablehnen, Folgetag erlauben
            if (minutes < -30 || minutes > windowMinutes) {
                return 0;
            }
        } else {
            long absMinutes = Math.abs(minutes);
            if (absMinutes > windowMinutes) {
                return 0;
            }
        }
        long absMinutes = Math.abs(minutes);
        int proximity = Math.max(8, 60 - (int) (absMinutes / (kind == LeitstellenMailKind.ABSCHLUSS ? 12 : 6)));
        score += proximity;
        if (minutes >= -10) {
            score += kind == LeitstellenMailKind.ABSCHLUSS ? 8 : 12;
        }
        if (faxStyle) {
            score += 5;
        }
        return score;
    }

    private static int textBonus(IncidentReport report, String haystack) {
        int score = 0;
        String foreignId = trimToNull(report.getDiveraForeignId());
        if (foreignId != null) {
            String foreignNorm = normalize(foreignId);
            if (!foreignNorm.isBlank() && haystack.contains(foreignNorm)) {
                score += 100;
            }
        }
        String incidentNumber = trimToNull(report.getIncidentNumber());
        if (incidentNumber != null && haystack.contains(normalize(incidentNumber))) {
            score += 40;
        }
        String street = trimToNull(report.getStreet());
        if (street != null && street.length() >= 4 && haystack.contains(normalize(street))) {
            score += 25;
        }
        String house = trimToNull(report.getHouseNumber());
        if (house != null && haystack.contains(normalize(house))) {
            score += 10;
        }
        String postal = trimToNull(report.getPostalCode());
        if (postal != null && haystack.contains(normalize(postal))) {
            score += 15;
        }
        String location = trimToNull(report.getLocation());
        if (location != null && location.length() >= 3 && haystack.contains(normalize(location))) {
            score += 10;
        }
        String stichwort = trimToNull(report.getStichwort());
        if (stichwort != null && stichwort.length() >= 4 && haystack.contains(normalize(stichwort))) {
            score += 20;
        }
        return score;
    }

    private static long bestTimeDistanceMinutes(
            IncidentReport report, LeitstellenImapClient.MailMessage mail, LeitstellenMailKind kind) {
        Instant anchor = kind == LeitstellenMailKind.ABSCHLUSS ? endInstant(report) : alarmInstant(report);
        if (anchor == null) {
            anchor = alarmInstant(report);
        }
        if (anchor == null || mail.receivedAt() == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs(Duration.between(anchor, mail.receivedAt()).toMinutes());
    }

    private static Instant alarmInstant(IncidentReport report) {
        return IncidentReportTimeSupport.resolveAlarmInstant(report);
    }

    private static Instant endInstant(IncidentReport report) {
        return IncidentReportTimeSupport.resolveEndInstant(report);
    }

    private static boolean containsAnyKeyword(String haystack, String keywordsCsv) {
        if (keywordsCsv == null || keywordsCsv.isBlank()) {
            return false;
        }
        for (String raw : keywordsCsv.split(",")) {
            String keyword = normalize(raw);
            if (!keyword.isBlank() && haystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return nullToEmpty(value).toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ").trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Scored(IncidentReport report, LeitstellenMailKind kind, int score, boolean ownershipOnly) {}
}
