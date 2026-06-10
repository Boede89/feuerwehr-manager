package de.feuerwehr.manager.berichte;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentNumberSupportTest {

    @Test
    void suggestUsesHighestSequenceInYear() {
        var existing = List.of("2026-01-15-09", "2026-08-02-03", "2025-12-31-99");
        String suggested = IncidentNumberSupport.suggestForDate(LocalDate.of(2026, 3, 2), existing);
        assertEquals("2026-03-02-10", suggested);
    }

    @Test
    void suggestPadsBelow100AndExpandsAt100() {
        assertEquals("2026-04-01-99", IncidentNumberSupport.suggestForDate(
                LocalDate.of(2026, 4, 1), List.of("2026-01-01-98")));
        assertEquals("2026-04-01-100", IncidentNumberSupport.suggestForDate(
                LocalDate.of(2026, 4, 1), List.of("2026-01-01-99")));
        assertEquals("2026-04-01-101", IncidentNumberSupport.suggestForDate(
                LocalDate.of(2026, 4, 1), List.of("2026-01-01-100")));
    }
}
