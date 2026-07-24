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
    void matchesByForeignIdAndClassifiesAbschluss() {
        UnitLeitstellenMailSettings settings = new UnitLeitstellenMailSettings();
        settings.setMatchWindowHours(12);
        settings.setDepescheKeywords("depesche,alarm");
        settings.setAbschlussKeywords("abschluss,abschlussbericht");

        IncidentReport report = new IncidentReport();
        report.setId(1L);
        report.setDiveraForeignId("ILS-12345");
        report.setIncidentDate(LocalDate.of(2026, 7, 24));
        report.setAlarmTime(LocalTime.of(14, 30));
        report.setStreet("Hauptstraße");
        report.setHouseNumber("12");

        var mail = new LeitstellenImapClient.MailMessage(
                "<msg-1>",
                9L,
                "Abschlussbericht ILS-12345 Hauptstraße 12",
                "leitstelle@example.de",
                Instant.parse("2026-07-24T13:10:00Z"),
                List.of());
        var pdf = new LeitstellenImapClient.PdfAttachment("bericht.pdf", new byte[] {1, 2, 3});

        Optional<LeitstellenMailMatcher.MatchResult> match =
                matcher.match(settings, mail, pdf, List.of(report));

        assertTrue(match.isPresent());
        assertEquals(LeitstellenMailKind.ABSCHLUSS, match.get().kind());
        assertTrue(match.get().score() >= 100);
    }
}
