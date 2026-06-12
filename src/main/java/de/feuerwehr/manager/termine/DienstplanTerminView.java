package de.feuerwehr.manager.termine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

public record DienstplanTerminView(
        long id,
        LocalDate datum,
        String thema,
        LocalTime dienstBeginn,
        LocalTime dienstEnde,
        String ausbilderName,
        String personenGruppenLabel,
        boolean audienceAll,
        List<Long> instructorPersonIds,
        List<Long> audienceGroupIds,
        List<Long> audiencePersonIds) {

    public String instructorPersonIdsCsv() {
        return joinIds(instructorPersonIds);
    }

    public String audienceGroupIdsCsv() {
        return joinIds(audienceGroupIds);
    }

    public String audiencePersonIdsCsv() {
        return joinIds(audiencePersonIds);
    }

    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
