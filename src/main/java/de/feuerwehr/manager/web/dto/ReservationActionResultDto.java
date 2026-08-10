package de.feuerwehr.manager.web.dto;

import de.feuerwehr.manager.reservierungen.LoeschfahrzeugWarningView;
import de.feuerwehr.manager.reservierungen.ReservationConflictView;
import java.util.List;

public record ReservationActionResultDto(
        boolean ok,
        String message,
        String code,
        List<ReservationConflictView> conflicts,
        LoeschfahrzeugWarningView loeschWarning,
        List<String> syncNotes,
        List<Long> conflictingResourceIds) {

    public static ReservationActionResultDto success(String message) {
        return new ReservationActionResultDto(true, message, null, List.of(), null, List.of(), List.of());
    }

    public static ReservationActionResultDto success(String message, List<String> syncNotes) {
        return new ReservationActionResultDto(
                true,
                message,
                null,
                List.of(),
                null,
                syncNotes == null ? List.of() : List.copyOf(syncNotes),
                List.of());
    }

    public static ReservationActionResultDto failure(String message) {
        return new ReservationActionResultDto(false, message, "ERROR", List.of(), null, List.of(), List.of());
    }

    public static ReservationActionResultDto conflicts(String message, List<ReservationConflictView> conflicts) {
        return conflicts(message, conflicts, List.of());
    }

    public static ReservationActionResultDto conflicts(
            String message, List<ReservationConflictView> conflicts, List<Long> conflictingResourceIds) {
        return new ReservationActionResultDto(
                false,
                message,
                "CONFLICTS",
                conflicts == null ? List.of() : List.copyOf(conflicts),
                null,
                List.of(),
                conflictingResourceIds == null ? List.of() : List.copyOf(conflictingResourceIds));
    }

    public static ReservationActionResultDto loeschWarning(LoeschfahrzeugWarningView warning) {
        return new ReservationActionResultDto(
                false,
                warning != null ? warning.message() : "Löschfahrzeug-Warnung",
                "LOESCH_WARNING",
                List.of(),
                warning,
                List.of(),
                List.of());
    }
}
