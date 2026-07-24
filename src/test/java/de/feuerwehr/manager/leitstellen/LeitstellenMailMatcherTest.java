package de.feuerwehr.manager.leitstellen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.feuerwehr.manager.berichte.IncidentReport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LeitstellenMailMatcherTest {

    private final LeitstellenMailMatcher matcher = new LeitstellenMailMatcher();

    @Test
    void firstFaxNearAlarmIsDepesche() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = report();

        var mail = faxMail(Instant.parse("2026-07-24T12:40:00Z")); // 14:40 Berlin, Alarm 14:30
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> false, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.DEPESCHE, match.get().kind());
    }

    @Test
    void faxNearEndIsAbschlussByTime() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = report();

        var mail = faxMail(Instant.parse("2026-07-24T14:05:00Z")); // nahe Ende 16:00 Berlin
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> false, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    @Test
    void sameNearAlarmMailNotForcedToAbschlussWhenDepescheExists() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = report();

        var mail = faxMail(Instant.parse("2026-07-24T12:40:00Z"));
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isEmpty());
    }

    @Test
    void mailBelongsToEarlierEinsatzNotLaterOne() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport earlier = report();
        earlier.setId(17L);
        earlier.setIncidentDate(LocalDate.of(2026, 7, 17));
        earlier.setAlarmTime(LocalTime.of(10, 0));
        earlier.setEndTime(LocalTime.of(12, 0));

        IncidentReport later = report();
        later.setId(19L);
        later.setIncidentDate(LocalDate.of(2026, 7, 19));
        later.setAlarmTime(LocalTime.of(14, 30));
        later.setEndTime(LocalTime.of(16, 0));

        var mail = faxMail(Instant.parse("2026-07-17T08:10:00Z"));
        Optional<LeitstellenMailMatcher.MatchResult> match = matcher.match(
                settings,
                mail,
                pdf(),
                List.of(earlier, later),
                id -> id == 17L,
                id -> id == 17L);

        assertTrue(match.isEmpty());
    }

    @Test
    void mailMatchesEarlierIncompleteEinsatz() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport earlier = report();
        earlier.setId(17L);
        earlier.setIncidentDate(LocalDate.of(2026, 7, 17));
        earlier.setAlarmTime(LocalTime.of(10, 0));
        earlier.setEndTime(LocalTime.of(12, 0));

        IncidentReport later = report();
        later.setId(19L);
        later.setIncidentDate(LocalDate.of(2026, 7, 19));
        later.setAlarmTime(LocalTime.of(14, 30));
        later.setEndTime(LocalTime.of(16, 0));

        var mail = faxMail(Instant.parse("2026-07-17T08:10:00Z"));
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(earlier, later), id -> false, id -> false);

        assertTrue(match.isPresent());
        assertEquals(17L, match.get().report().getId());
        assertEquals(LeitstellenMailKind.DEPESCHE, match.get().kind());
    }

    @Test
    void overnightEinsatzAbschlussNextMorning() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = new IncidentReport();
        report.setId(2L);
        report.setIncidentDate(LocalDate.of(2026, 7, 24));
        report.setAlarmTime(LocalTime.of(22, 15));
        report.setEndTime(LocalTime.of(1, 40)); // nach Mitternacht → Folgetag

        // Abschluss am nächsten Vormittag (Berlin 09:20)
        var mail = faxMail(Instant.parse("2026-07-25T07:20:00Z"));
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    @Test
    void abschlussSameEveningButMailNextAfternoonStillMatches() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = report();
        report.setAlarmTime(LocalTime.of(20, 0));
        report.setEndTime(LocalTime.of(22, 30));

        // ~15 h nach Ende — innerhalb 36-h-Abschlussfenster
        var mail = faxMail(Instant.parse("2026-07-25T11:30:00Z")); // 13:30 Berlin
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    @Test
    void abschlussExactlyAtMidnightNextDayWhenDepescheExists() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = new IncidentReport();
        report.setId(3L);
        report.setIncidentDate(LocalDate.of(2026, 6, 6));
        report.setAlarmTime(LocalTime.of(22, 10));
        report.setEndTime(LocalTime.of(23, 40));

        // 7. Juni 2026 00:00 Europe/Berlin
        var mail = faxMail(Instant.parse("2026-06-06T22:00:00Z"));
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    @Test
    void abschlussAtMidnightWithEndTimeMidnightOvernight() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = new IncidentReport();
        report.setId(4L);
        report.setIncidentDate(LocalDate.of(2026, 6, 6));
        report.setAlarmTime(LocalTime.of(21, 0));
        report.setEndTime(LocalTime.MIDNIGHT); // 00:00 → Folgetag

        var mail = faxMail(Instant.parse("2026-06-06T22:00:00Z")); // 00:00 Berlin
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    @Test
    void abschlussNextDayWithoutEndTimeWhenDepescheExists() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = new IncidentReport();
        report.setId(5L);
        report.setIncidentDate(LocalDate.of(2026, 6, 6));
        report.setAlarmTime(LocalTime.of(20, 0));
        report.setEndTime(null);

        var mail = faxMail(Instant.parse("2026-06-06T22:00:00Z"));
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    private static UnitLeitstellenMailSettings baseSettings() {
        UnitLeitstellenMailSettings settings = new UnitLeitstellenMailSettings();
        settings.setMatchWindowHours(12);
        settings.setDepescheKeywords("depesche,alarmdepesche");
        settings.setAbschlussKeywords("abschluss,abschlussbericht");
        return settings;
    }

    private static IncidentReport report() {
        IncidentReport report = new IncidentReport();
        report.setId(1L);
        report.setIncidentDate(LocalDate.of(2026, 7, 24));
        report.setAlarmTime(LocalTime.of(14, 30));
        report.setEndTime(LocalTime.of(16, 0));
        return report;
    }

    private static LeitstellenImapClient.MailMessage faxMail(Instant receivedAt) {
        return new LeitstellenImapClient.MailMessage(
                "<msg-1>",
                9L,
                "FWD:[Feuerwehr Schwalmtal] FAX image from:[+49 2162 5300053]",
                "fax@example.de",
                receivedAt,
                List.of());
    }

    private static LeitstellenImapClient.PdfAttachment pdf() {
        return new LeitstellenImapClient.PdfAttachment("image.pdf", new byte[] {1, 2, 3});
    }
}
