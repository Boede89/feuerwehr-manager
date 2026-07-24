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
    void matchesFaxSubjectByTimeAndUsesOrderForKind() {
        UnitLeitstellenMailSettings settings = new UnitLeitstellenMailSettings();
        settings.setMatchWindowHours(12);
        settings.setDepescheKeywords("depesche,alarmdepesche");
        settings.setAbschlussKeywords("abschluss,abschlussbericht");

        IncidentReport report = new IncidentReport();
        report.setId(1L);
        report.setIncidentDate(LocalDate.of(2026, 7, 24));
        report.setAlarmTime(LocalTime.of(14, 30));

        var mail = new LeitstellenImapClient.MailMessage(
                "<msg-1>",
                9L,
                "FWD:[Feuerwehr Schwalmtal] FAX image from:[+49 2162 5300053]",
                "fax@example.de",
                Instant.parse("2026-07-24T12:40:00Z"),
                List.of());
        var pdf = new LeitstellenImapClient.PdfAttachment("image.pdf", new byte[] {1, 2, 3});

        Optional<LeitstellenMailMatcher.MatchResult> first = matcher.match(
                settings, mail, pdf, List.of(report), id -> false, id -> false);
        assertTrue(first.isPresent());
        assertEquals(LeitstellenMailKind.DEPESCHE, first.get().kind());

        Optional<LeitstellenMailMatcher.MatchResult> second = matcher.match(
                settings, mail, pdf, List.of(report), id -> true, id -> false);
        assertTrue(second.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, second.get().kind());
    }
}
