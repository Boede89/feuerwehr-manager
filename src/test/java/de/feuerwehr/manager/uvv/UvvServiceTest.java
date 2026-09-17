package de.feuerwehr.manager.uvv;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UvvServiceTest {

    @Test
    void missingWhenNeverCompleted() {
        assertThat(UvvService.computeLevel(null, 12, 30, LocalDate.of(2026, 6, 1)))
                .isEqualTo(UvvStatusLevel.MISSING);
    }

    @Test
    void overdueWarnAndOkByInterval() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        LocalDate last = LocalDate.of(2025, 6, 1);

        assertThat(UvvService.computeLevel(last, 12, 30, today)).isEqualTo(UvvStatusLevel.OVERDUE);

        LocalDate soon = today.minusMonths(12).plusDays(10);
        assertThat(UvvService.computeLevel(soon, 12, 30, today)).isEqualTo(UvvStatusLevel.WARN);

        LocalDate fresh = today.minusMonths(3);
        assertThat(UvvService.computeLevel(fresh, 12, 30, today)).isEqualTo(UvvStatusLevel.OK);
    }

    @Test
    void nextDueAddsIntervalMonths() {
        assertThat(UvvService.nextDueOn(null, 12)).isNull();
        assertThat(UvvService.nextDueOn(LocalDate.of(2025, 3, 15), 12)).isEqualTo(LocalDate.of(2026, 3, 15));
    }
}
