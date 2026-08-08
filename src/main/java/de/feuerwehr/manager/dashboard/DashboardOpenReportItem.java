package de.feuerwehr.manager.dashboard;

import java.time.LocalDate;

/** Eintrag im Dashboard-Widget „Offene Berichte“. */
public record DashboardOpenReportItem(
        String kind,
        String kindLabel,
        String title,
        LocalDate date,
        String number,
        String href) {}
