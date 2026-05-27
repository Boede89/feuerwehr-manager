package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveraAlarmsResponse(boolean success, String message, List<DiveraAlarmSummary> alarms) {

    public static DiveraAlarmsResponse ok(List<DiveraAlarmSummary> alarms) {
        return new DiveraAlarmsResponse(true, "OK", alarms);
    }

    public static DiveraAlarmsResponse fail(String message) {
        return new DiveraAlarmsResponse(false, message, List.of());
    }
}
