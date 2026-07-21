package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.unit.UnitAdminService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservierungenConflictService {

    private final VehicleReservationRepository vehicleReservationRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final ReservierungenSettingsService settingsService;
    private final UnitAdminService unitAdminService;

    @Transactional(readOnly = true)
    public List<ReservationConflictView> vehicleConflicts(long vehicleId, Instant startAt, Instant endAt, Long excludeId) {
        return vehicleReservationRepository.findApprovedConflicts(vehicleId, startAt, endAt, excludeId).stream()
                .map(r -> new ReservationConflictView(
                        r.getId(),
                        ReservationKind.VEHICLE,
                        r.getVehicle().getName(),
                        r.getRequesterName(),
                        r.getStartAt(),
                        r.getEndAt(),
                        r.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> roomConflicts(long roomId, Instant startAt, Instant endAt, Long excludeId) {
        return roomReservationRepository.findApprovedConflicts(roomId, startAt, endAt, excludeId).stream()
                .map(r -> new ReservationConflictView(
                        r.getId(),
                        ReservationKind.ROOM,
                        r.getRoom().getName(),
                        r.getRequesterName(),
                        r.getStartAt(),
                        r.getEndAt(),
                        r.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoeschfahrzeugWarningView checkLoeschfahrzeugWarning(
            long unitId, long vehicleId, Instant startAt, Instant endAt, Long excludeReservationId) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        if (!settings.isVehicleLoeschWarnEnabled()) {
            return noWarning();
        }
        List<Long> loeschIds = settingsService.loeschVehicleIds(settings);
        if (loeschIds.isEmpty()) {
            return noWarning();
        }
        if (!loeschIds.contains(vehicleId)) {
            return noWarning();
        }
        int total = loeschIds.size();
        int minAvailable = Math.max(0, settings.getVehicleLoeschMinAvailable());
        Set<Long> reservedLoesch = new HashSet<>();
        for (Long loeschId : loeschIds) {
            if (vehicleReservationRepository
                    .findApprovedConflicts(loeschId, startAt, endAt, excludeReservationId)
                    .isEmpty()) {
                continue;
            }
            reservedLoesch.add(loeschId);
        }
        reservedLoesch.add(vehicleId);
        int reservedAfter = reservedLoesch.size();
        int remainingAfter = Math.max(0, total - reservedAfter);
        if (remainingAfter >= minAvailable) {
            return noWarning();
        }
        return new LoeschfahrzeugWarningView(
                true,
                total,
                reservedAfter,
                remainingAfter,
                minAvailable,
                "Warnung: Nach Genehmigung wären nur noch "
                        + remainingAfter
                        + " von "
                        + total
                        + " Löschfahrzeugen verfügbar (Mindestwert: "
                        + minAvailable
                        + ").");
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listBookableVehicles(long unitId) {
        return unitAdminService.listVehicles(unitId).stream()
                .filter(Vehicle::isActive)
                .toList();
    }

    private static LoeschfahrzeugWarningView noWarning() {
        return new LoeschfahrzeugWarningView(false, 0, 0, 0, 0, null);
    }
}
