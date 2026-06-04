package de.feuerwehr.manager.divera;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveraAlarmsResponse(
        boolean success, String message, List<DiveraAlarmSummary> alarms, Map<Long, String> rawJsonByAlarmId) {

    public DiveraAlarmsResponse(boolean success, String message, List<DiveraAlarmSummary> alarms) {
        this(success, message, alarms, Map.of());
    }

    public static DiveraAlarmsResponse ok(List<DiveraAlarmSummary> alarms, Map<Long, String> rawJsonByAlarmId) {
        return new DiveraAlarmsResponse(true, "OK", alarms, rawJsonByAlarmId != null ? rawJsonByAlarmId : Map.of());
    }

    public static DiveraAlarmsResponse ok(List<DiveraAlarmSummary> alarms) {
        return ok(alarms, Map.of());
    }

    public static DiveraAlarmsResponse fail(String message) {
        return new DiveraAlarmsResponse(false, message, List.of(), Map.of());
    }
}
