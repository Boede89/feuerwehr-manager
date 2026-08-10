package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.technik.Room;
import de.feuerwehr.manager.technik.RoomRepository;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.unit.UnitAdminService;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private final VehicleRepository vehicleRepository;
    private final RoomRepository roomRepository;
    private final ReservierungenSettingsService settingsService;
    private final UnitAdminService unitAdminService;

    @Transactional(readOnly = true)
    public List<ReservationConflictView> vehicleConflicts(
            long vehicleId, Instant startAt, Instant endAt, Long excludeId) {
        return vehicleConflicts(vehicleId, startAt, endAt, excludeId == null ? Set.of() : Set.of(excludeId));
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> vehicleConflicts(
            long vehicleId, Instant startAt, Instant endAt, Set<Long> excludeIds) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
        if (vehicle == null) {
            return List.of();
        }
        Set<Long> excludes = excludeIds == null ? Set.of() : excludeIds;
        List<Long> relatedIds = relatedVehicleIds(vehicle);
        return vehicleReservationRepository
                .findByStatusAndAnyVehicleIdIn(ReservationStatus.APPROVED, relatedIds)
                .stream()
                .filter(r -> r.getId() == null || !excludes.contains(r.getId()))
                .filter(r -> overlaps(r.getStartAt(), r.getEndAt(), startAt, endAt))
                .map(r -> new ReservationConflictView(
                        r.getId(),
                        ReservationKind.VEHICLE,
                        r.vehicleNamesJoined(),
                        r.getRequesterName(),
                        r.getReason(),
                        r.getStartAt(),
                        r.getEndAt(),
                        r.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> vehicleConflictsForVehicles(
            List<Long> vehicleIds, Instant startAt, Instant endAt, Long excludeId) {
        return vehicleConflictsForVehicles(
                vehicleIds, startAt, endAt, excludeId == null ? Set.of() : Set.of(excludeId));
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> vehicleConflictsForVehicles(
            List<Long> vehicleIds, Instant startAt, Instant endAt, Set<Long> excludeIds) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<ReservationConflictView> result = new java.util.ArrayList<>();
        if (vehicleIds == null) {
            return List.of();
        }
        for (Long vehicleId : vehicleIds) {
            if (vehicleId == null) {
                continue;
            }
            for (ReservationConflictView conflict : vehicleConflicts(vehicleId, startAt, endAt, excludeIds)) {
                if (seen.add(conflict.id())) {
                    result.add(conflict);
                }
            }
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> roomConflicts(long roomId, Instant startAt, Instant endAt, Long excludeId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return List.of();
        }
        List<Long> relatedIds = relatedRoomIds(room);
        return roomReservationRepository
                .findByRoomIdInAndStatus(relatedIds, ReservationStatus.APPROVED)
                .stream()
                .filter(r -> excludeId == null || !excludeId.equals(r.getId()))
                .filter(r -> overlaps(r.getStartAt(), r.getEndAt(), startAt, endAt))
                .map(r -> new ReservationConflictView(
                        r.getId(),
                        ReservationKind.ROOM,
                        r.getRoom().getName(),
                        r.getRequesterName(),
                        r.getReason(),
                        r.getStartAt(),
                        r.getEndAt(),
                        r.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoeschfahrzeugWarningView checkLoeschfahrzeugWarning(
            long unitId, long vehicleId, Instant startAt, Instant endAt, Long excludeReservationId) {
        return checkLoeschfahrzeugWarning(unitId, List.of(vehicleId), startAt, endAt, excludeReservationId);
    }

    @Transactional(readOnly = true)
    public LoeschfahrzeugWarningView checkLoeschfahrzeugWarning(
            long unitId, List<Long> vehicleIds, Instant startAt, Instant endAt, Long excludeReservationId) {
        return checkLoeschfahrzeugWarning(
                unitId,
                vehicleIds,
                startAt,
                endAt,
                excludeReservationId == null ? Set.of() : Set.of(excludeReservationId));
    }

    @Transactional(readOnly = true)
    public LoeschfahrzeugWarningView checkLoeschfahrzeugWarning(
            long unitId,
            List<Long> vehicleIds,
            Instant startAt,
            Instant endAt,
            Set<Long> excludeReservationIds) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        if (!settings.isVehicleLoeschWarnEnabled()) {
            return noWarning();
        }
        List<Long> loeschIds = settingsService.loeschVehicleIds(settings);
        if (loeschIds.isEmpty() || vehicleIds == null || vehicleIds.isEmpty()) {
            return noWarning();
        }
        Set<Long> requestedLoesch = new HashSet<>();
        for (Long vehicleId : vehicleIds) {
            if (vehicleId == null) {
                continue;
            }
            Vehicle requested = vehicleRepository.findById(vehicleId).orElse(null);
            if (requested == null) {
                if (loeschIds.contains(vehicleId)) {
                    requestedLoesch.add(vehicleId);
                }
                continue;
            }
            for (Long related : relatedVehicleIds(requested)) {
                if (loeschIds.contains(related)) {
                    requestedLoesch.add(related);
                }
            }
        }
        if (requestedLoesch.isEmpty()) {
            return noWarning();
        }
        Set<Long> excludes = excludeReservationIds == null ? Set.of() : excludeReservationIds;
        int total = loeschIds.size();
        int minAvailable = Math.max(0, settings.getVehicleLoeschMinAvailable());
        Set<Long> reservedLoesch = new HashSet<>();
        for (Long loeschId : loeschIds) {
            if (vehicleConflicts(loeschId, startAt, endAt, excludes).isEmpty()) {
                continue;
            }
            reservedLoesch.add(loeschId);
        }
        reservedLoesch.addAll(requestedLoesch);
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
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        List<Vehicle> vehicles = unitAdminService.listVehicles(unitId).stream()
                .filter(Vehicle::isActive)
                .toList();
        return settingsService.sortVehicles(settings, vehicles);
    }

    List<Long> relatedVehicleIds(Vehicle vehicle) {
        long root = vehicle.getProductionSourceId() != null ? vehicle.getProductionSourceId() : vehicle.getId();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(vehicle.getId());
        ids.add(root);
        ids.addAll(vehicleRepository.findFamilyIds(root));
        return List.copyOf(ids);
    }

    List<Long> relatedRoomIds(Room room) {
        long root = room.getProductionSourceId() != null ? room.getProductionSourceId() : room.getId();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(room.getId());
        ids.add(root);
        ids.addAll(roomRepository.findFamilyIds(root));
        return List.copyOf(ids);
    }

    static boolean overlaps(Instant existingStart, Instant existingEnd, Instant startAt, Instant endAt) {
        if (existingStart == null || existingEnd == null || startAt == null || endAt == null) {
            return false;
        }
        return existingStart.isBefore(endAt) && existingEnd.isAfter(startAt);
    }

    private static LoeschfahrzeugWarningView noWarning() {
        return new LoeschfahrzeugWarningView(false, 0, 0, 0, 0, null);
    }
}
