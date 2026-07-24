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
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String haystack = normalize(
                nullToEmpty(mail.subject())
                        + " "
                        + nullToEmpty(mail.fromAddress())
                        + " "
                        + nullToEmpty(pdf.filename()));
        LeitstellenMailKind kind = classify(settings, haystack);

        List<Scored> scored = new ArrayList<>();
        for (IncidentReport report : candidates) {
            int score = scoreReport(report, mail, haystack, settings.getMatchWindowHours());
            if (score > 0) {
                scored.add(new Scored(report, score));
            }
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        Scored best = scored.get(0);
        if (scored.size() > 1 && scored.get(1).score() == best.score() && best.score() < 80) {
            // Unklarer Gleichstand ohne starken Treffer → lieber warten
            return Optional.empty();
        }
        if (best.score() < 25) {
            return Optional.empty();
        }
        return Optional.of(new MatchResult(best.report(), kind, best.score()));
    }

    LeitstellenMailKind classify(UnitLeitstellenMailSettings settings, String haystack) {
        if (containsAnyKeyword(haystack, settings.getAbschlussKeywords())) {
            return LeitstellenMailKind.ABSCHLUSS;
        }
        if (containsAnyKeyword(haystack, settings.getDepescheKeywords())) {
            return LeitstellenMailKind.DEPESCHE;
        }
        // Fallback: Abschlussberichte kommen typischerweise später
        return LeitstellenMailKind.DEPESCHE;
    }

    private static int scoreReport(
            IncidentReport report, LeitstellenImapClient.MailMessage mail, String haystack, int matchWindowHours) {
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
            long hours = Math.abs(Duration.between(alarmInstant, mail.receivedAt()).toHours());
            int window = Math.max(1, matchWindowHours);
            if (hours <= window) {
                score += Math.max(5, 50 - (int) hours * 3);
            } else {
                return 0;
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
        return score;
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

    private record Scored(IncidentReport report, int score) {}
}
