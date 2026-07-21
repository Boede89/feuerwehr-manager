package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.technik.Room;
import de.feuerwehr.manager.technik.RoomRepository;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitAdminService;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservierungenService {

    private final VehicleReservationRepository vehicleReservationRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final VehicleRepository vehicleRepository;
    private final RoomRepository roomRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final UnitAdminService unitAdminService;
    private final ReservierungenSettingsService settingsService;
    private final ReservierungenConflictService conflictService;
    private final ReservierungenNotificationService notificationService;
    private final ReservierungenDiveraSyncService diveraSyncService;
    private final ReservierungenGoogleCalendarService googleCalendarService;

    @Transactional(readOnly = true)
    public List<ReservationListItemView> listMine(long unitId, long userId) {
        List<ReservationListItemView> items = new ArrayList<>();
        for (VehicleReservation reservation : vehicleReservationRepository.findByUnitIdAndRequesterUserIdOrderByStartAtDesc(unitId, userId)) {
            items.add(toView(reservation, userId));
        }
        for (RoomReservation reservation : roomReservationRepository.findByUnitIdAndRequesterUserIdOrderByStartAtDesc(unitId, userId)) {
            items.add(toView(reservation, userId));
        }
        items.sort(Comparator.comparing(ReservationListItemView::startAt).reversed());
        return items;
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemView> listPending(long unitId, long currentUserId) {
        List<ReservationListItemView> items = new ArrayList<>();
        for (VehicleReservation reservation : vehicleReservationRepository.findByUnitIdAndStatusOrderByStartAtAsc(unitId, ReservationStatus.PENDING)) {
            items.add(toView(reservation, currentUserId));
        }
        for (RoomReservation reservation : roomReservationRepository.findByUnitIdAndStatusOrderByStartAtAsc(unitId, ReservationStatus.PENDING)) {
            items.add(toView(reservation, currentUserId));
        }
        items.sort(Comparator.comparing(ReservationListItemView::startAt));
        return items;
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemView> listAll(long unitId, long currentUserId) {
        List<ReservationListItemView> items = new ArrayList<>();
        for (VehicleReservation reservation : vehicleReservationRepository.findByUnitIdOrderByStartAtDesc(unitId)) {
            items.add(toView(reservation, currentUserId));
        }
        for (RoomReservation reservation : roomReservationRepository.findByUnitIdOrderByStartAtDesc(unitId)) {
            items.add(toView(reservation, currentUserId));
        }
        items.sort(Comparator.comparing(ReservationListItemView::startAt).reversed());
        return items;
    }

    @Transactional
    public VehicleReservation createVehicleReservation(long unitId, long userId, CreateReservationRequest request) {
        validateTimes(request.startAt(), request.endAt());
        Vehicle vehicle = vehicleRepository
                .findByIdAndUnitId(request.resourceId(), unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        if (!vehicle.isActive()) {
            throw new IllegalArgumentException("Fahrzeug ist nicht aktiv.");
        }
        LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                unitId, vehicle.getId(), request.startAt(), request.endAt(), null);
        if (warning.warning() && !request.forceAvailabilityOverride()) {
            throw new IllegalArgumentException(warning.message());
        }
        VehicleReservation reservation = new VehicleReservation();
        reservation.setUnit(requireUnit(unitId));
        reservation.setVehicle(vehicle);
        reservation.setRequesterUser(requireUser(userId));
        reservation.setRequesterName(requireText(request.requesterName(), "Antragsteller"));
        reservation.setRequesterEmail(requireText(request.requesterEmail(), "E-Mail"));
        reservation.setReason(requireText(request.reason(), "Grund"));
        reservation.setLocation(trimToNull(request.location()));
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        reservation.setStatus(ReservationStatus.PENDING);
        VehicleReservation saved = vehicleReservationRepository.save(reservation);
        notificationService.notifyAdminsNewVehicleReservation(unitId, saved);
        return saved;
    }

    @Transactional
    public RoomReservation createRoomReservation(long unitId, long userId, CreateReservationRequest request) {
        validateTimes(request.startAt(), request.endAt());
        Room room = roomRepository
                .findByIdAndUnitId(request.resourceId(), unitId)
                .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        if (!room.isActive()) {
            throw new IllegalArgumentException("Raum ist nicht aktiv.");
        }
        RoomReservation reservation = new RoomReservation();
        reservation.setUnit(requireUnit(unitId));
        reservation.setRoom(room);
        reservation.setRequesterUser(requireUser(userId));
        reservation.setRequesterName(requireText(request.requesterName(), "Antragsteller"));
        reservation.setRequesterEmail(requireText(request.requesterEmail(), "E-Mail"));
        reservation.setReason(requireText(request.reason(), "Grund"));
        reservation.setLocation(trimToNull(request.location()));
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        reservation.setStatus(ReservationStatus.PENDING);
        RoomReservation saved = roomReservationRepository.save(reservation);
        notificationService.notifyAdminsNewRoomReservation(unitId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> checkVehicleConflicts(long unitId, long reservationId) {
        VehicleReservation reservation = requirePendingVehicle(unitId, reservationId);
        return conflictService.vehicleConflicts(
                reservation.getVehicle().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> checkRoomConflicts(long unitId, long reservationId) {
        RoomReservation reservation = requirePendingRoom(unitId, reservationId);
        return conflictService.roomConflicts(
                reservation.getRoom().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
    }

    @Transactional
    public void processVehicleReservation(long unitId, long reservationId, long actorUserId, ProcessReservationRequest request) {
        VehicleReservation reservation = requirePendingVehicle(unitId, reservationId);
        String action = normalizeAction(request.action());
        if ("reject".equals(action)) {
            rejectVehicle(reservation, actorUserId, request.reason());
            return;
        }
        if ("approve".equals(action) || "approve_with_conflict_resolution".equals(action)) {
            approveVehicle(reservation, actorUserId, request);
        } else {
            throw new IllegalArgumentException("Unbekannte Aktion: " + request.action());
        }
    }

    @Transactional
    public void processRoomReservation(long unitId, long reservationId, long actorUserId, ProcessReservationRequest request) {
        RoomReservation reservation = requirePendingRoom(unitId, reservationId);
        String action = normalizeAction(request.action());
        if ("reject".equals(action)) {
            rejectRoom(reservation, actorUserId, request.reason());
            return;
        }
        if ("approve".equals(action) || "approve_with_conflict_resolution".equals(action)) {
            approveRoom(reservation, actorUserId, request);
        } else {
            throw new IllegalArgumentException("Unbekannte Aktion: " + request.action());
        }
    }

    @Transactional
    public void deleteVehicleReservation(long unitId, long reservationId) {
        VehicleReservation reservation = vehicleReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        cleanupVehicleReservation(reservation);
        vehicleReservationRepository.delete(reservation);
    }

    @Transactional
    public void deleteRoomReservation(long unitId, long reservationId) {
        RoomReservation reservation = roomReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        cleanupRoomReservation(reservation);
        roomReservationRepository.delete(reservation);
    }

    private void approveVehicle(VehicleReservation reservation, long actorUserId, ProcessReservationRequest request) {
        long unitId = reservation.getUnit().getId();
        List<ReservationConflictView> conflicts = conflictService.vehicleConflicts(
                reservation.getVehicle().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (!conflicts.isEmpty() && !"approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            throw new IllegalStateException("Konflikte vorhanden – Genehmigung mit Konfliktlösung erforderlich.");
        }
        LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                unitId, reservation.getVehicle().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (warning.warning() && !request.forceAvailabilityOverride()) {
            throw new IllegalArgumentException(warning.message());
        }
        if ("approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            cancelVehicleConflicts(unitId, conflicts, request.conflictIds());
        }
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        vehicleReservationRepository.save(reservation);
        applyVehicleIntegrations(unitId, reservation, actorUserId, request.diveraGroupIds());
        notificationService.notifyRequesterApproved(
                unitId, reservation.getRequesterEmail(), "Fahrzeug", reservation.getVehicle().getName());
    }

    private void approveRoom(RoomReservation reservation, long actorUserId, ProcessReservationRequest request) {
        long unitId = reservation.getUnit().getId();
        List<ReservationConflictView> conflicts = conflictService.roomConflicts(
                reservation.getRoom().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (!conflicts.isEmpty() && !"approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            throw new IllegalStateException("Konflikte vorhanden – Genehmigung mit Konfliktlösung erforderlich.");
        }
        if ("approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            cancelRoomConflicts(unitId, conflicts, request.conflictIds());
        }
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        roomReservationRepository.save(reservation);
        applyRoomIntegrations(unitId, reservation, actorUserId);
        notificationService.notifyRequesterApproved(
                unitId, reservation.getRequesterEmail(), "Raum", reservation.getRoom().getName());
    }

    private void rejectVehicle(VehicleReservation reservation, long actorUserId, String reason) {
        reservation.setStatus(ReservationStatus.REJECTED);
        reservation.setRejectionReason(trimToNull(reason));
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        vehicleReservationRepository.save(reservation);
        notificationService.notifyRequesterRejected(
                reservation.getUnit().getId(),
                reservation.getRequesterEmail(),
                "Fahrzeug",
                reservation.getVehicle().getName(),
                reason);
    }

    private void rejectRoom(RoomReservation reservation, long actorUserId, String reason) {
        reservation.setStatus(ReservationStatus.REJECTED);
        reservation.setRejectionReason(trimToNull(reason));
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        roomReservationRepository.save(reservation);
        notificationService.notifyRequesterRejected(
                reservation.getUnit().getId(),
                reservation.getRequesterEmail(),
                "Raum",
                reservation.getRoom().getName(),
                reason);
    }

    private void cancelVehicleConflicts(long unitId, List<ReservationConflictView> conflicts, List<Long> conflictIds) {
        for (ReservationConflictView conflict : conflicts) {
            if (conflictIds != null && !conflictIds.isEmpty() && !conflictIds.contains(conflict.id())) {
                continue;
            }
            VehicleReservation existing = vehicleReservationRepository.findById(conflict.id()).orElse(null);
            if (existing == null || existing.getStatus() != ReservationStatus.APPROVED) {
                continue;
            }
            existing.setStatus(ReservationStatus.CANCELLED);
            vehicleReservationRepository.save(existing);
            cleanupVehicleReservation(existing);
            notificationService.notifyRequesterCancelled(
                    unitId, existing.getRequesterEmail(), "Fahrzeug", existing.getVehicle().getName());
        }
    }

    private void cancelRoomConflicts(long unitId, List<ReservationConflictView> conflicts, List<Long> conflictIds) {
        for (ReservationConflictView conflict : conflicts) {
            if (conflictIds != null && !conflictIds.isEmpty() && !conflictIds.contains(conflict.id())) {
                continue;
            }
            RoomReservation existing = roomReservationRepository.findById(conflict.id()).orElse(null);
            if (existing == null || existing.getStatus() != ReservationStatus.APPROVED) {
                continue;
            }
            existing.setStatus(ReservationStatus.CANCELLED);
            roomReservationRepository.save(existing);
            cleanupRoomReservation(existing);
            notificationService.notifyRequesterCancelled(
                    unitId, existing.getRequesterEmail(), "Raum", existing.getRoom().getName());
        }
    }

    private void applyVehicleIntegrations(
            long unitId, VehicleReservation reservation, long actorUserId, List<Integer> diveraGroupIds) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        if (settings.isVehicleDiveraEnabled()) {
            List<Integer> groups = diveraGroupIds != null && !diveraGroupIds.isEmpty()
                    ? diveraGroupIds
                    : settingsService.defaultDiveraGroupIds(settings, false);
            diveraSyncService.syncVehicleReservation(reservation, groups, actorUserId).ifPresent(reservation::setDiveraEventId);
            vehicleReservationRepository.save(reservation);
        }
        if (settings.isVehicleGoogleCalendarEnabled()) {
            googleCalendarService.syncVehicleReservation(unitId, reservation);
        }
    }

    private void applyRoomIntegrations(long unitId, RoomReservation reservation, long actorUserId) {
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        if (settings.isRoomDiveraEnabled()) {
            diveraSyncService.syncRoomReservation(reservation, actorUserId).ifPresent(reservation::setDiveraEventId);
            roomReservationRepository.save(reservation);
        }
        if (settings.isRoomGoogleCalendarEnabled()) {
            googleCalendarService.syncRoomReservation(unitId, reservation);
        }
    }

    private void cleanupVehicleReservation(VehicleReservation reservation) {
        long unitId = reservation.getUnit().getId();
        diveraSyncService.deleteEvent(unitId, reservation.getDiveraEventId(), null);
        googleCalendarService.deleteReservationCalendarEvent(ReservationKind.VEHICLE, reservation.getId());
    }

    private void cleanupRoomReservation(RoomReservation reservation) {
        long unitId = reservation.getUnit().getId();
        diveraSyncService.deleteEvent(unitId, reservation.getDiveraEventId(), null);
        googleCalendarService.deleteReservationCalendarEvent(ReservationKind.ROOM, reservation.getId());
    }

    private VehicleReservation requirePendingVehicle(long unitId, long reservationId) {
        VehicleReservation reservation = vehicleReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Reservierung wurde bereits bearbeitet.");
        }
        return reservation;
    }

    private RoomReservation requirePendingRoom(long unitId, long reservationId) {
        RoomReservation reservation = roomReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Reservierung wurde bereits bearbeitet.");
        }
        return reservation;
    }

    private ReservationListItemView toView(VehicleReservation reservation, long currentUserId) {
        return new ReservationListItemView(
                reservation.getId(),
                ReservationKind.VEHICLE,
                reservation.getVehicle().getName(),
                reservation.getRequesterName(),
                reservation.getRequesterEmail(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus(),
                reservation.getRejectionReason(),
                reservation.getApprovedAt(),
                reservation.getApprovedByUser() != null ? reservation.getApprovedByUser().getDisplayName() : null,
                reservation.getRequesterUser() != null
                        && Objects.equals(reservation.getRequesterUser().getId(), currentUserId));
    }

    private ReservationListItemView toView(RoomReservation reservation, long currentUserId) {
        return new ReservationListItemView(
                reservation.getId(),
                ReservationKind.ROOM,
                reservation.getRoom().getName(),
                reservation.getRequesterName(),
                reservation.getRequesterEmail(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus(),
                reservation.getRejectionReason(),
                reservation.getApprovedAt(),
                reservation.getApprovedByUser() != null ? reservation.getApprovedByUser().getDisplayName() : null,
                reservation.getRequesterUser() != null
                        && Objects.equals(reservation.getRequesterUser().getId(), currentUserId));
    }

    private Unit requireUnit(long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private User requireUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
    }

    private static void validateTimes(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("Start- und Endzeit sind erforderlich.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Endzeit muss nach Startzeit liegen.");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " ist erforderlich.");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    }
}
