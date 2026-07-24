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
    void secondFaxIsAbschlussWhenDepescheExists() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = report();

        var mail = faxMail(Instant.parse("2026-07-24T14:20:00Z")); // nach Ende 16:00 Berlin ≈ 14:00 UTC
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> true, id -> false);

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
    }

    @Test
    void faxNearEndWithoutDepescheIsAbschlussByTime() {
        UnitLeitstellenMailSettings settings = baseSettings();
        IncidentReport report = report();

        var mail = faxMail(Instant.parse("2026-07-24T14:05:00Z")); // nahe Ende 16:00 Berlin
        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf(), List.of(report), id -> false, id -> false);

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
