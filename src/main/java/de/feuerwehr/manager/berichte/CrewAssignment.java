package de.feuerwehr.manager.berichte;

import java.util.List;

public record CrewAssignment(long vehicleId, List<Long> personIds) {}
