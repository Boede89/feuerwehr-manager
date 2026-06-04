package de.feuerwehr.manager.divera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Manueller Abruf aller Divera-Alarme (Poll). Persistenz als Einsatzbericht folgt mit Modul „Berichte“.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiveraImportService {

    private final DiveraService diveraService;

    public DiveraImportResult importAlarmsForUnit(long unitId) {
        DiveraAlarmsResponse response = diveraService.getAlarmsForUnit(unitId);
        if (!response.success()) {
            return DiveraImportResult.fail(response.message());
        }
        int total = response.alarms().size();
        for (DiveraAlarmSummary alarm : response.alarms()) {
            log.info(
                    "[Divera-Import] unit={} alarmId={} title={} closed={}",
                    unitId,
                    alarm.id(),
                    alarm.title(),
                    alarm.closed());
        }
        String hint = total == 0
                ? "Keine Alarme von DIVERA geliefert."
                : total + " Alarm(e) abgerufen. Anlage als Einsatzbericht folgt mit dem Modul „Berichte“.";
        return DiveraImportResult.ok(total, 0, hint);
    }
}
