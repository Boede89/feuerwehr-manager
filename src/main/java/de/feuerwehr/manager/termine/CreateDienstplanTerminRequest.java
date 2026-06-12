package de.feuerwehr.manager.termine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateDienstplanTerminRequest(
        LocalDate terminDatum,
        String thema,
        LocalTime dienstBeginn,
        LocalTime dienstEnde,
        List<Long> instructorPersonIds,
        Boolean audienceAll,
        List<Long> personIds,
        List<Long> groupIds) {

    public boolean appliesToAll() {
        return audienceAll == null || audienceAll;
    }
}
