package de.feuerwehr.manager.berichte;

import java.util.List;

public record AttendanceCheckInPageView(
        long reportId,
        long terminId,
        long unitId,
        String theme,
        String categoryLabel,
        String startTimeLabel,
        List<PersonTile> availablePersons,
        List<PersonTile> checkedInPersons) {

    public record PersonTile(long id, String displayName) {}
}
