package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.IncidentReport;
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

@Component
public class LeitstellenMailMatcher {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public record MatchResult(IncidentReport report, LeitstellenMailKind kind, int score) {}

    public Optional<MatchResult> match(
            UnitLeitstellenMailSettings settings,
            LeitstellenImapClient.MailMessage mail,
            LeitstellenImapClient.PdfAttachment pdf,
            List<IncidentReport> candidates) {
        return match(settings, mail, pdf, candidates, reportId -> false, reportId -> false);
    }

    /**
     * @param hasDepesche prüft, ob zum Bericht schon eine Depeche importiert wurde
     * @param hasAbschluss prüft, ob schon ein Abschlussbericht importiert wurde
     */
    public Optional<MatchResult> match(
            UnitLeitstellenMailSettings settings,
            LeitstellenImapClient.MailMessage mail,
            LeitstellenImapClient.PdfAttachment pdf,
            List<IncidentReport> candidates,
            Function<Long, Boolean> hasDepesche,
            Function<Long, Boolean> hasAbschluss) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String haystack = normalize(
                nullToEmpty(mail.subject())
                        + " "
                        + nullToEmpty(mail.fromAddress())
                        + " "
                        + nullToEmpty(pdf.filename()));
        LeitstellenMailKind kindHint = classify(settings, haystack);
        boolean faxStyle = haystack.contains("fax");

        List<Scored> scored = new ArrayList<>();
        for (IncidentReport report : candidates) {
            int score = scoreReport(report, mail, haystack, settings.getMatchWindowHours(), faxStyle);
            if (score <= 0) {
                continue;
            }
            boolean depescheDone = Boolean.TRUE.equals(hasDepesche.apply(report.getId()));
            boolean abschlussDone = Boolean.TRUE.equals(hasAbschluss.apply(report.getId()));
            // Prefer reports that still need the next document
            if (!depescheDone) {
                score += 8;
            } else if (!abschlussDone) {
                score += 6;
            } else {
                // Already has both — only keep if explicit replace is intended; skip by default
                continue;
            }
            scored.add(new Scored(report, score, depescheDone, abschlussDone));
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingInt(Scored::score)
                .reversed()
                .thenComparing(s -> minutesFromAlarm(s.report(), mail), Comparator.naturalOrder()));
        Scored best = scored.get(0);
        int minScore = faxStyle ? 12 : 20;
        if (best.score() < minScore) {
            return Optional.empty();
        }
        if (scored.size() > 1) {
            Scored second = scored.get(1);
            // Unklarer Gleichstand ohne starken Text-Treffer und ohne klaren Zeitvorsprung
            if (second.score() == best.score()
                    && best.score() < 80
                    && minutesFromAlarm(best.report(), mail) == minutesFromAlarm(second.report(), mail)) {
                return Optional.empty();
            }
        }
        LeitstellenMailKind kind = resolveKind(kindHint, best.depescheDone(), best.abschlussDone());
        return Optional.of(new MatchResult(best.report(), kind, best.score()));
    }

    LeitstellenMailKind classify(UnitLeitstellenMailSettings settings, String haystack) {
        if (containsAnyKeyword(haystack, settings.getAbschlussKeywords())) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        if (containsAnyKeyword(haystack, settings.getDepescheKeywords())) {
            return LeitstellenMailKind.DEPESCHE;
        }
        return LeitstellenMailKind.DEPESCHE;
    }

    private static LeitstellenMailKind resolveKind(
            LeitstellenMailKind hint, boolean depescheDone, boolean abschlussDone) {
        if (hint == LeitstellenMailKind.ABSCHLUSS && !abschlussDone) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        if (hint == LeitstellenMailKind.DEPESCHE && !depescheDone) {
            return LeitstellenMailKind.DEPESCHE;
        }
        if (!depescheDone) {
            return LeitstellenMailKind.DEPESCHE;
        }
        return LeitstellenMailKind.ABSCHLUSS;
    }

    private static int scoreReport(
            IncidentReport report,
            LeitstellenImapClient.MailMessage mail,
            String haystack,
            int matchWindowHours,
            boolean faxStyle) {
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
        Instant alarmInstant = alarmInstant(report);
        if (alarmInstant != null && mail.receivedAt() != null) {
            long minutes = Duration.between(alarmInstant, mail.receivedAt()).toMinutes();
            long absMinutes = Math.abs(minutes);
            long windowMinutes = Math.max(1, matchWindowHours) * 60L;
            if (absMinutes > windowMinutes) {
                return 0;
            }
            // Näher an der Alarmzeit = besser (Minuten-Auflösung, wichtig bei FAX-Betreff)
            score += Math.max(10, 55 - (int) (absMinutes / 8));
            if (minutes >= 0) {
                score += 12; // Mail nach Alarm bevorzugen
            } else if (minutes >= -15) {
                score += 4; // leichte Vorlaufzeit ok (Leitstelle/Fax-Verzögerung)
            }
        } else if (report.getIncidentDate() != null && mail.receivedAt() != null) {
            LocalDate mailDate = mail.receivedAt().atZone(ZONE).toLocalDate();
            long days = Math.abs(Duration.between(
                            report.getIncidentDate().atStartOfDay(ZONE).toInstant(),
                            mailDate.atStartOfDay(ZONE).toInstant())
                    .toDays());
            if (days > Math.max(1, matchWindowHours / 24 + 1)) {
                return 0;
            }
            score += 15;
        }
        score += addressBonus(report, haystack);
        String stichwort = trimToNull(report.getStichwort());
        if (stichwort != null && stichwort.length() >= 4 && haystack.contains(normalize(stichwort))) {
            score += 20;
        }
        if (faxStyle) {
            score += 5;
        }
        return score;
    }

    private static long minutesFromAlarm(IncidentReport report, LeitstellenImapClient.MailMessage mail) {
        Instant alarm = alarmInstant(report);
        if (alarm == null || mail.receivedAt() == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs(Duration.between(alarm, mail.receivedAt()).toMinutes());
    }

    private static int addressBonus(IncidentReport report, String haystack) {
        int bonus = 0;
        String street = trimToNull(report.getStreet());
        if (street != null && street.length() >= 4 && haystack.contains(normalize(street))) {
            bonus += 25;
        }
        String house = trimToNull(report.getHouseNumber());
        if (house != null && haystack.contains(normalize(house))) {
            bonus += 10;
        }
        String postal = trimToNull(report.getPostalCode());
        if (postal != null && haystack.contains(normalize(postal))) {
            bonus += 15;
        }
        String location = trimToNull(report.getLocation());
        if (location != null && location.length() >= 3 && haystack.contains(normalize(location))) {
            bonus += 10;
        }
        return bonus;
    }

    private static Instant alarmInstant(IncidentReport report) {
        if (report.getIncidentDate() == null) {
            return null;
        }
        LocalTime time = report.getAlarmTime() != null ? report.getAlarmTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(report.getIncidentDate(), time).atZone(ZONE).toInstant();
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

    private record Scored(IncidentReport report, int score, boolean depescheDone, boolean abschlussDone) {}
}
