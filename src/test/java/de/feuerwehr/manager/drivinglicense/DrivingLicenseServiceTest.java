package de.feuerwehr.manager.drivinglicense;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DrivingLicenseServiceTest {

    @Test
    void missingWhenNoLicenseOrUnknown() {
        assertThat(DrivingLicenseService.computeLevel(null, 30, LocalDate.of(2026, 1, 1)))
                .isEqualTo(DrivingLicenseStatusLevel.MISSING);

        DrivingLicense unknown = new DrivingLicense();
        unknown.setPresence(DrivingLicensePresence.UNKNOWN);
        assertThat(DrivingLicenseService.computeLevel(unknown, 30, LocalDate.of(2026, 1, 1)))
                .isEqualTo(DrivingLicenseStatusLevel.MISSING);
    }

    @Test
    void pendingWhenYesButNotYetChecked() {
        DrivingLicense license = new DrivingLicense();
        license.setPresence(DrivingLicensePresence.YES);
        license.setClassesCsv("B");
        license.setNextDueOn(null);
        assertThat(DrivingLicenseService.computeLevel(license, 30, LocalDate.of(2026, 6, 1)))
                .isEqualTo(DrivingLicenseStatusLevel.PENDING);
    }

    @Test
    void noneWhenNoLicenseDeclared() {
        DrivingLicense none = new DrivingLicense();
        none.setPresence(DrivingLicensePresence.NO);
        assertThat(DrivingLicenseService.computeLevel(none, 30, LocalDate.of(2026, 1, 1)))
                .isEqualTo(DrivingLicenseStatusLevel.NONE);
    }

    @Test
    void overdueWarnAndOkByDueDate() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        DrivingLicense license = new DrivingLicense();
        license.setPresence(DrivingLicensePresence.YES);

        license.setNextDueOn(today.minusDays(1));
        assertThat(DrivingLicenseService.computeLevel(license, 30, today))
                .isEqualTo(DrivingLicenseStatusLevel.OVERDUE);

        license.setNextDueOn(today.plusDays(10));
        assertThat(DrivingLicenseService.computeLevel(license, 30, today))
                .isEqualTo(DrivingLicenseStatusLevel.WARN);

        license.setNextDueOn(today.plusDays(60));
        assertThat(DrivingLicenseService.computeLevel(license, 30, today))
                .isEqualTo(DrivingLicenseStatusLevel.OK);
    }

    @Test
    void overdueWhenExpiresBeforeToday() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        DrivingLicense license = new DrivingLicense();
        license.setPresence(DrivingLicensePresence.YES);
        license.setNextDueOn(today.plusMonths(6));
        license.setExpiresOn(today.minusDays(1));
        assertThat(DrivingLicenseService.computeLevel(license, 30, today))
                .isEqualTo(DrivingLicenseStatusLevel.OVERDUE);
    }

    @Test
    void parseAndSerializeClasses() {
        assertThat(DrivingLicenseClass.toCsvFromCodes(new String[] {"a", "b", "CE", "X"}))
                .isEqualTo("A,B,CE");
        assertThat(DrivingLicenseClass.parseCsv("A, B, BE ,C1"))
                .containsExactly(
                        DrivingLicenseClass.A,
                        DrivingLicenseClass.B,
                        DrivingLicenseClass.BE,
                        DrivingLicenseClass.C1);
    }
}
