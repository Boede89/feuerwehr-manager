package de.feuerwehr.manager.atemschutz;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AtemschutzNotificationSectionView {
    private final AtemschutzNotificationCategory category;
    private final int warnDays;
    private final boolean notifyInstructors;
    private final boolean notifyCarriers;
    private final List<Long> ccPersonIds;
    private final AtemschutzEmailTemplate warnungTemplate;
    private final AtemschutzEmailTemplate abgelaufenTemplate;
}
