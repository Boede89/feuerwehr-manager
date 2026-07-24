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

/**
 * Zuordnung Leitstellen-FAX → Einsatzbericht.
 * Depeche nahe Alarmbeginn, Abschlussbericht nahe Einsatzende (größeres Zeitfenster).
 * Liegt schon eine Depeche vor, ist die nächste Mail der Abschlussbericht.
 */
@Component
public class LeitstellenMailMatcher {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

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
        // Abschlussfax oft später/weiter vom Ende entfernt als Depeche vom Beginn
        int abschlussWindowHours = Math.max(baseWindowHours, (int) Math.ceil(baseWindowHours * 1.5));

        List<Scored> scored = new ArrayList<>();
        for (IncidentReport report : candidates) {
            boolean depescheDone = Boolean.TRUE.equals(hasDepesche.apply(report.getId()));
            boolean abschlussDone = Boolean.TRUE.equals(hasAbschluss.apply(report.getId()));

            // Besitz anhand der Zeit (unabhängig davon, ob Dateien schon da sind) —
            // verhindert, dass Mails eines früheren Einsatzes einem späteren zugeordnet werden.
            LeitstellenMailKind ownershipKind = decideKind(settings, haystack, report, mail, false);
            int ownershipScore =
                    scoreForKind(report, mail, haystack, ownershipKind, baseWindowHours, abschlussWindowHours, faxStyle);
            if (ownershipScore <= 0) {
                continue;
            }

            LeitstellenMailKind attachKind = ownershipKind;
            if (depescheDone && abschlussDone) {
                // Nur zur Disambiguierung mitzählen, nicht anhängen
                scored.add(new Scored(report, ownershipKind, ownershipScore, true));
                continue;
            }
            if (attachKind == LeitstellenMailKind.DEPESCHE && depescheDone) {
                attachKind = LeitstellenMailKind.ABSCHLUSS;
            }
            if (attachKind == LeitstellenMailKind.ABSCHLUSS && abschlussDone) {
                if (!depescheDone) {
                    attachKind = LeitstellenMailKind.DEPESCHE;
                    int depescheScore = scoreForKind(
                            report, mail, haystack, LeitstellenMailKind.DEPESCHE, baseWindowHours, abschlussWindowHours, faxStyle);
                    if (depescheScore <= 0) {
                        scored.add(new Scored(report, ownershipKind, ownershipScore, true));
                        continue;
                    }
                    ownershipScore = depescheScore;
                } else {
                    scored.add(new Scored(report, ownershipKind, ownershipScore, true));
                    continue;
                }
            }
            int attachScore = scoreForKind(
                    report, mail, haystack, attachKind, baseWindowHours, abschlussWindowHours, faxStyle);
            if (attachScore <= 0) {
                scored.add(new Scored(report, ownershipKind, ownershipScore, true));
                continue;
            }
            scored.add(new Scored(report, attachKind, Math.max(ownershipScore, attachScore), false));
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingInt(Scored::score)
                .reversed()
                .thenComparing(s -> bestTimeDistanceMinutes(s.report(), mail, s.kind()), Comparator.naturalOrder()));

        Scored best = scored.get(0);
        // Anderer Einsatz ist zeitlich näher → dieser Mail-Anhang gehört nicht hierher
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
     * 1) Depeche schon da → nur noch Abschluss möglich.
     * 2) Explizite Stichworte im Betreff/Dateiname.
     * 3) Zeitnähe: näher am Alarmbeginn = Depeche, näher am Einsatzende = Abschluss.
     */
    private LeitstellenMailKind decideKind(
            UnitLeitstellenMailSettings settings,
            String haystack,
            IncidentReport report,
            LeitstellenImapClient.MailMessage mail,
            boolean depescheDone) {
        if (depescheDone) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
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
        if (alarm != null && end != null && received != null) {
            long toAlarm = Math.abs(Duration.between(alarm, received).toMinutes());
            long toEnd = Math.abs(Duration.between(end, received).toMinutes());
            // Bei Unentschieden: eher Depeche (kommt zuerst)
            if (toEnd + 10 < toAlarm) {
                return LeitstellenMailKind.ABSCHLUSS;
            }
            return LeitstellenMailKind.DEPESCHE;
        }
        if (end != null && alarm != null && received != null && !received.isBefore(end.minus(Duration.ofMinutes(5)))) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        return LeitstellenMailKind.DEPESCHE;
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
        // Fallback: ohne Ende trotzdem über Alarm zuordenbar (weiteres Fenster)
        if (anchor == null && kind == LeitstellenMailKind.ABSCHLUSS) {
            anchor = alarmInstant(report);
        }
        if (anchor == null || mail.receivedAt() == null) {
            if (report.getIncidentDate() != null) {
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
        long absMinutes = Math.abs(minutes);
        long windowMinutes = (kind == LeitstellenMailKind.ABSCHLUSS ? abschlussWindowHours : depescheWindowHours)
                * 60L;
        if (absMinutes > windowMinutes) {
            return 0;
        }
        // Näher am Anker = besser
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
        if (report.getIncidentDate() == null) {
            return null;
        }
        LocalTime time = report.getAlarmTime() != null ? report.getAlarmTime() : LocalTime.MIDNIGHT;
        return LocalDateTime.of(report.getIncidentDate(), time).atZone(ZONE).toInstant();
    }

    private static Instant endInstant(IncidentReport report) {
        if (report.getIncidentDate() == null || report.getEndTime() == null) {
            return null;
        }
        LocalDateTime end = LocalDateTime.of(report.getIncidentDate(), report.getEndTime());
        // Mitternacht-Überlauf: Ende vor Alarm → Folgetag
        if (report.getAlarmTime() != null && report.getEndTime().isBefore(report.getAlarmTime())) {
            end = end.plusDays(1);
        }
        return end.atZone(ZONE).toInstant();
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
