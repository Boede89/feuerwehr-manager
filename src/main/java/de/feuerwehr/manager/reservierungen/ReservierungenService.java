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
import java.util.LinkedHashSet;
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
    public List<VehicleReservation> createVehicleReservation(long unitId, long userId, CreateReservationRequest request) {
        List<Long> vehicleIds = resolveResourceIds(request);
        List<CreateReservationRequest.ReservationTimeSlot> slots = resolveSlots(request);
        if (vehicleIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Fahrzeug wählen.");
        }
        String requesterName = requireText(request.requesterName(), "Antragsteller");
        String requesterEmail = requireText(request.requesterEmail(), "E-Mail");
        String reason = requireText(request.reason(), "Grund");
        String location = requireText(request.location(), "Ort / Standort");
        Unit unit = requireUnit(unitId);
        User requester = requireUser(userId);

        List<Vehicle> vehicles = new ArrayList<>();
        for (Long vehicleId : vehicleIds) {
            Vehicle vehicle = vehicleRepository
                    .findByIdAndUnitId(vehicleId, unitId)
                    .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
            if (!vehicle.isActive()) {
                throw new IllegalArgumentException("Fahrzeug \"" + vehicle.getName() + "\" ist nicht aktiv.");
            }
            vehicles.add(vehicle);
        }

        List<ReservationConflictView> allConflicts = new ArrayList<>();
        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            validateTimes(slot.startAt(), slot.endAt());
            allConflicts.addAll(conflictService.vehicleConflictsForVehicles(
                    vehicleIds, slot.startAt(), slot.endAt(), null));
        }
        if (!allConflicts.isEmpty() && !request.forceConflictOverride()) {
            throw new ReservationConflictException(
                    "Mindestens ein Fahrzeug ist in einem der Zeiträume bereits vergeben.",
                    distinctConflicts(allConflicts));
        }

        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                    unitId, vehicleIds, slot.startAt(), slot.endAt(), null);
            if (warning.warning() && !request.forceAvailabilityOverride()) {
                throw new LoeschfahrzeugWarningException(warning);
            }
        }

        List<VehicleReservation> saved = new ArrayList<>();
        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            VehicleReservation reservation = new VehicleReservation();
            reservation.setUnit(unit);
            reservation.setVehiclesOrdered(vehicles);
            reservation.setRequesterUser(requester);
            reservation.setRequesterName(requesterName);
            reservation.setRequesterEmail(requesterEmail);
            reservation.setReason(reason);
            reservation.setLocation(location);
            reservation.setStartAt(slot.startAt());
            reservation.setEndAt(slot.endAt());
            reservation.setStatus(ReservationStatus.PENDING);
            saved.add(vehicleReservationRepository.save(reservation));
        }
        if (!saved.isEmpty()) {
            notificationService.notifyAdminsNewVehicleReservation(unitId, saved.get(0), saved.size());
        }
        return saved;
    }

    @Transactional
    public List<RoomReservation> createRoomReservation(long unitId, long userId, CreateReservationRequest request) {
        List<Long> roomIds = resolveResourceIds(request);
        List<CreateReservationRequest.ReservationTimeSlot> slots = resolveSlots(request);
        if (roomIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Raum wählen.");
        }
        String requesterName = requireText(request.requesterName(), "Antragsteller");
        String requesterEmail = requireText(request.requesterEmail(), "E-Mail");
        String reason = requireText(request.reason(), "Grund");
        String location = requireText(request.location(), "Ort / Standort");
        Unit unit = requireUnit(unitId);
        User requester = requireUser(userId);

        List<Room> rooms = new ArrayList<>();
        for (Long roomId : roomIds) {
            Room room = roomRepository
                    .findByIdAndUnitId(roomId, unitId)
                    .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
            if (!room.isActive()) {
                throw new IllegalArgumentException("Raum \"" + room.getName() + "\" ist nicht aktiv.");
            }
            rooms.add(room);
        }

        List<ReservationConflictView> allConflicts = new ArrayList<>();
        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            validateTimes(slot.startAt(), slot.endAt());
            for (Room room : rooms) {
                allConflicts.addAll(conflictService.roomConflicts(room.getId(), slot.startAt(), slot.endAt(), null));
            }
        }
        if (!allConflicts.isEmpty() && !request.forceConflictOverride()) {
            throw new ReservationConflictException(
                    "Mindestens ein Raum ist in einem der Zeiträume bereits vergeben.",
                    distinctConflicts(allConflicts));
        }

        List<RoomReservation> saved = new ArrayList<>();
        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            for (Room room : rooms) {
                RoomReservation reservation = new RoomReservation();
                reservation.setUnit(unit);
                reservation.setRoom(room);
                reservation.setRequesterUser(requester);
                reservation.setRequesterName(requesterName);
                reservation.setRequesterEmail(requesterEmail);
                reservation.setReason(reason);
                reservation.setLocation(location);
                reservation.setStartAt(slot.startAt());
                reservation.setEndAt(slot.endAt());
                reservation.setStatus(ReservationStatus.PENDING);
                saved.add(roomReservationRepository.save(reservation));
            }
        }
        if (!saved.isEmpty()) {
            notificationService.notifyAdminsNewRoomReservation(unitId, saved.get(0), saved.size());
        }
        return saved;
    }

    @Transactional
    public List<String> importApprovedReservation(long unitId, long actorUserId, ImportReservationRequest request) {
        String kind = request.kind() == null ? "" : request.kind().trim().toLowerCase(Locale.ROOT);
        if (!"vehicle".equals(kind) && !"room".equals(kind) && !"fahrzeug".equals(kind) && !"raum".equals(kind)) {
            throw new IllegalArgumentException("Bitte Art Fahrzeug oder Raum wählen.");
        }
        boolean vehicleKind = "vehicle".equals(kind) || "fahrzeug".equals(kind);
        List<Long> resourceIds = new ArrayList<>();
        if (request.resourceIds() != null) {
            for (Long id : request.resourceIds()) {
                if (id != null && id > 0) {
                    resourceIds.add(id);
                }
            }
        }
        if (resourceIds.isEmpty() && request.resourceId() != null && request.resourceId() > 0) {
            resourceIds.add(request.resourceId());
        }
        if (resourceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    vehicleKind ? "Bitte mindestens ein Fahrzeug wählen." : "Bitte einen Raum wählen.");
        }
        validateTimes(request.startAt(), request.endAt());
        String requesterName = requireText(request.requesterName(), "Antragsteller");
        String requesterEmail = requireText(request.requesterEmail(), "E-Mail");
        String reason = requireText(request.reason(), "Grund");
        String location = requireText(request.location(), "Ort / Standort");
        Unit unit = requireUnit(unitId);
        User actor = requireUser(actorUserId);
        List<String> notes = new ArrayList<>();

        if (vehicleKind) {
            List<Vehicle> vehicles = new ArrayList<>();
            for (Long vehicleId : resourceIds) {
                Vehicle vehicle = vehicleRepository
                        .findByIdAndUnitId(vehicleId, unitId)
                        .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
                if (!vehicle.isActive()) {
                    throw new IllegalArgumentException("Fahrzeug \"" + vehicle.getName() + "\" ist nicht aktiv.");
                }
                vehicles.add(vehicle);
            }
            VehicleReservation reservation = new VehicleReservation();
            reservation.setUnit(unit);
            reservation.setVehiclesOrdered(vehicles);
            reservation.setRequesterUser(actor);
            reservation.setRequesterName(requesterName);
            reservation.setRequesterEmail(requesterEmail);
            reservation.setReason(reason);
            reservation.setLocation(location);
            reservation.setStartAt(request.startAt());
            reservation.setEndAt(request.endAt());
            reservation.setStatus(ReservationStatus.APPROVED);
            reservation.setApprovedByUser(actor);
            reservation.setApprovedAt(Instant.now());
            VehicleReservation saved = vehicleReservationRepository.save(reservation);
            if (request.syncCalendars()) {
                notes.addAll(applyVehicleIntegrations(unitId, saved, actorUserId, null));
            } else {
                notes.add("Kalender-Sync übersprungen.");
            }
            if (request.sendRequesterEmail()) {
                notificationService.notifyRequesterDecision(unitId, saved, true, null);
                notes.add("Antragsteller per E-Mail informiert.");
            } else {
                notes.add("Keine E-Mail an Antragsteller gesendet.");
            }
            return notes;
        }

        if (resourceIds.size() != 1) {
            throw new IllegalArgumentException("Für Räume bitte genau einen Raum wählen.");
        }
        Room room = roomRepository
                .findByIdAndUnitId(resourceIds.get(0), unitId)
                .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        if (!room.isActive()) {
            throw new IllegalArgumentException("Raum ist nicht aktiv.");
        }
        RoomReservation reservation = new RoomReservation();
        reservation.setUnit(unit);
        reservation.setRoom(room);
        reservation.setRequesterUser(actor);
        reservation.setRequesterName(requesterName);
        reservation.setRequesterEmail(requesterEmail);
        reservation.setReason(reason);
        reservation.setLocation(location);
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(actor);
        reservation.setApprovedAt(Instant.now());
        RoomReservation saved = roomReservationRepository.save(reservation);
        if (request.syncCalendars()) {
            notes.addAll(applyRoomIntegrations(unitId, saved, actorUserId));
        } else {
            notes.add("Kalender-Sync übersprungen.");
        }
        if (request.sendRequesterEmail()) {
            notificationService.notifyRequesterDecision(unitId, saved, true, null);
            notes.add("Antragsteller per E-Mail informiert.");
        } else {
            notes.add("Keine E-Mail an Antragsteller gesendet.");
        }
        return notes;
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> checkVehicleConflicts(long unitId, long reservationId) {
        VehicleReservation reservation = requirePendingVehicle(unitId, reservationId);
        List<Long> vehicleIds = reservation.resolvedVehicles().stream().map(Vehicle::getId).toList();
        return conflictService.vehicleConflictsForVehicles(
                vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> checkRoomConflicts(long unitId, long reservationId) {
        RoomReservation reservation = requirePendingRoom(unitId, reservationId);
        return conflictService.roomConflicts(
                reservation.getRoom().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
    }

    @Transactional
    public List<String> processVehicleReservation(
            long unitId, long reservationId, long actorUserId, ProcessReservationRequest request) {
        VehicleReservation reservation = requirePendingVehicle(unitId, reservationId);
        String action = normalizeAction(request.action());
        if ("reject".equals(action)) {
            rejectVehicle(reservation, actorUserId, request.reason());
            return List.of();
        }
        if ("approve".equals(action) || "approve_with_conflict_resolution".equals(action)) {
            return approveVehicle(reservation, actorUserId, request);
        }
        throw new IllegalArgumentException("Unbekannte Aktion: " + request.action());
    }

    @Transactional
    public List<String> processRoomReservation(
            long unitId, long reservationId, long actorUserId, ProcessReservationRequest request) {
        RoomReservation reservation = requirePendingRoom(unitId, reservationId);
        String action = normalizeAction(request.action());
        if ("reject".equals(action)) {
            rejectRoom(reservation, actorUserId, request.reason());
            return List.of();
        }
        if ("approve".equals(action) || "approve_with_conflict_resolution".equals(action)) {
            return approveRoom(reservation, actorUserId, request);
        }
        throw new IllegalArgumentException("Unbekannte Aktion: " + request.action());
    }

    @Transactional
    public void deleteVehicleReservation(long unitId, long reservationId) {
        VehicleReservation reservation = vehicleReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        ReservationStatus status = reservation.getStatus();
        String email = reservation.getRequesterEmail();
        String vehicleName = reservation.vehicleNamesJoined();
        String reason = reservation.getReason();
        String location = reservation.getLocation();
        Instant startAt = reservation.getStartAt();
        Instant endAt = reservation.getEndAt();
        cleanupVehicleReservation(reservation);
        vehicleReservationRepository.delete(reservation);
        if (status == ReservationStatus.APPROVED || status == ReservationStatus.PENDING) {
            notificationService.notifyRequesterCancelled(
                    unitId,
                    email,
                    "Fahrzeug",
                    vehicleName,
                    reason,
                    location,
                    startAt,
                    endAt,
                    status == ReservationStatus.APPROVED
                            ? "Ihre genehmigte Fahrzeugreservierung wurde storniert und gelöscht."
                            : "Ihr Antrag auf eine Fahrzeugreservierung wurde gelöscht.");
        }
    }

    @Transactional
    public void deleteRoomReservation(long unitId, long reservationId) {
        RoomReservation reservation = roomReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        ReservationStatus status = reservation.getStatus();
        String email = reservation.getRequesterEmail();
        String roomName = reservation.getRoom().getName();
        String reason = reservation.getReason();
        String location = reservation.getLocation();
        Instant startAt = reservation.getStartAt();
        Instant endAt = reservation.getEndAt();
        cleanupRoomReservation(reservation);
        roomReservationRepository.delete(reservation);
        if (status == ReservationStatus.APPROVED || status == ReservationStatus.PENDING) {
            notificationService.notifyRequesterCancelled(
                    unitId,
                    email,
                    "Raum",
                    roomName,
                    reason,
                    location,
                    startAt,
                    endAt,
                    status == ReservationStatus.APPROVED
                            ? "Ihre genehmigte Raumreservierung wurde storniert und gelöscht."
                            : "Ihr Antrag auf eine Raumreservierung wurde gelöscht.");
        }
    }

    private List<String> approveVehicle(VehicleReservation reservation, long actorUserId, ProcessReservationRequest request) {
        long unitId = reservation.getUnit().getId();
        List<Long> vehicleIds = reservation.resolvedVehicles().stream().map(Vehicle::getId).toList();
        List<ReservationConflictView> conflicts = conflictService.vehicleConflictsForVehicles(
                vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (!conflicts.isEmpty() && !"approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            throw new ReservationConflictException(
                    "Das Fahrzeug ist in diesem Zeitraum bereits genehmigt belegt.", conflicts);
        }
        LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                unitId, vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (warning.warning() && !request.forceAvailabilityOverride()) {
            throw new LoeschfahrzeugWarningException(warning);
        }
        if ("approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            conflicts = conflictService.vehicleConflictsForVehicles(
                    vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
            cancelVehicleConflicts(unitId, conflicts, request.conflictIds());
        }
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        vehicleReservationRepository.save(reservation);
        List<String> syncNotes = applyVehicleIntegrations(unitId, reservation, actorUserId, request.diveraGroupIds());
        notificationService.notifyRequesterDecision(unitId, reservation, true, null);
        return syncNotes;
    }

    private List<String> approveRoom(RoomReservation reservation, long actorUserId, ProcessReservationRequest request) {
        long unitId = reservation.getUnit().getId();
        List<ReservationConflictView> conflicts = conflictService.roomConflicts(
                reservation.getRoom().getId(), reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (!conflicts.isEmpty() && !"approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            throw new ReservationConflictException(
                    "Der Raum ist in diesem Zeitraum bereits genehmigt belegt.", conflicts);
        }
        if ("approve_with_conflict_resolution".equals(normalizeAction(request.action()))) {
            conflicts = conflictService.roomConflicts(
                    reservation.getRoom().getId(),
                    reservation.getStartAt(),
                    reservation.getEndAt(),
                    reservation.getId());
            cancelRoomConflicts(unitId, conflicts, request.conflictIds());
        }
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        roomReservationRepository.save(reservation);
        List<String> syncNotes = applyRoomIntegrations(unitId, reservation, actorUserId);
        notificationService.notifyRequesterDecision(unitId, reservation, true, null);
        return syncNotes;
    }

    private void rejectVehicle(VehicleReservation reservation, long actorUserId, String reason) {
        reservation.setStatus(ReservationStatus.REJECTED);
        reservation.setRejectionReason(trimToNull(reason));
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        vehicleReservationRepository.save(reservation);
        notificationService.notifyRequesterDecision(
                reservation.getUnit().getId(), reservation, false, reason);
    }

    private void rejectRoom(RoomReservation reservation, long actorUserId, String reason) {
        reservation.setStatus(ReservationStatus.REJECTED);
        reservation.setRejectionReason(trimToNull(reason));
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        roomReservationRepository.save(reservation);
        notificationService.notifyRequesterDecision(
                reservation.getUnit().getId(), reservation, false, reason);
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
                    unitId,
                    existing.getRequesterEmail(),
                    "Fahrzeug",
                    existing.vehicleNamesJoined(),
                    existing.getReason(),
                    existing.getLocation(),
                    existing.getStartAt(),
                    existing.getEndAt(),
                    "Ihre genehmigte Fahrzeugreservierung wurde wegen eines Konflikts storniert.");
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
                    unitId,
                    existing.getRequesterEmail(),
                    "Raum",
                    existing.getRoom().getName(),
                    existing.getReason(),
                    existing.getLocation(),
                    existing.getStartAt(),
                    existing.getEndAt(),
                    "Ihre genehmigte Raumreservierung wurde wegen eines Konflikts storniert.");
        }
    }

    private List<String> applyVehicleIntegrations(
            long unitId, VehicleReservation reservation, long actorUserId, List<Integer> diveraGroupIds) {
        List<String> notes = new ArrayList<>();
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        if (!settings.isVehicleDiveraEnabled() && !settings.isVehicleGoogleCalendarEnabled()) {
            notes.add(
                    "Hinweis: DIVERA-/Google-Sync ist unter Reservierungen → Einstellungen nicht aktiviert.");
            return notes;
        }
        if (settings.isVehicleDiveraEnabled()) {
            List<Integer> groups = diveraGroupIds != null && !diveraGroupIds.isEmpty()
                    ? diveraGroupIds
                    : settingsService.defaultDiveraGroupIds(settings, false);
            var synced = diveraSyncService.syncVehicleReservation(reservation, groups, actorUserId);
            if (synced.isPresent()) {
                reservation.setDiveraEventId(synced.get());
                vehicleReservationRepository.save(reservation);
                notes.add("DIVERA: Termin angelegt.");
            } else {
                notes.add(
                        "DIVERA: Termin konnte nicht angelegt werden"
                                + " (persönlichen Access Key unter Einstellungen bzw."
                                + " Einheits-Key unter Admin → Schnittstellen prüfen; Server-Log).");
            }
        }
        if (settings.isVehicleGoogleCalendarEnabled()) {
            int created = googleCalendarService.syncVehicleReservation(
                    unitId, reservation, settingsService.vehicleGoogleCalendarAccountIds(settings));
            if (created > 0) {
                notes.add("Google Kalender: " + created + (created == 1 ? " Termin" : " Termine") + " angelegt.");
            } else {
                notes.add(
                        "Google Kalender: kein Termin angelegt"
                                + " (Kalender aktiv + Service-Account-JSON + Calendar-ID;"
                                + " Kalender mit client_email teilen; Server-Log prüfen).");
            }
        }
        return notes;
    }

    private List<String> applyRoomIntegrations(long unitId, RoomReservation reservation, long actorUserId) {
        List<String> notes = new ArrayList<>();
        UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
        if (!settings.isRoomDiveraEnabled() && !settings.isRoomGoogleCalendarEnabled()) {
            notes.add(
                    "Hinweis: DIVERA-/Google-Sync ist unter Reservierungen → Einstellungen nicht aktiviert.");
            return notes;
        }
        if (settings.isRoomDiveraEnabled()) {
            List<Integer> groups = settingsService.defaultDiveraGroupIds(settings, true);
            var synced = diveraSyncService.syncRoomReservation(reservation, groups, actorUserId);
            if (synced.isPresent()) {
                reservation.setDiveraEventId(synced.get());
                roomReservationRepository.save(reservation);
                notes.add("DIVERA: Termin angelegt.");
            } else {
                notes.add(
                        "DIVERA: Termin konnte nicht angelegt werden"
                                + " (persönlichen Access Key unter Einstellungen bzw."
                                + " Einheits-Key unter Admin → Schnittstellen prüfen; Server-Log).");
            }
        }
        if (settings.isRoomGoogleCalendarEnabled()) {
            int created = googleCalendarService.syncRoomReservation(
                    unitId, reservation, settingsService.roomGoogleCalendarAccountIds(settings));
            if (created > 0) {
                notes.add("Google Kalender: " + created + (created == 1 ? " Termin" : " Termine") + " angelegt.");
            } else {
                notes.add(
                        "Google Kalender: kein Termin angelegt"
                                + " (Kalender aktiv + Service-Account-JSON + Calendar-ID;"
                                + " Kalender mit client_email teilen; Server-Log prüfen).");
            }
        }
        return notes;
    }

    private void cleanupVehicleReservation(VehicleReservation reservation) {
        long unitId = reservation.getUnit().getId();
        Long actorUserId = reservation.getApprovedByUser() != null
                ? reservation.getApprovedByUser().getId()
                : (reservation.getRequesterUser() != null ? reservation.getRequesterUser().getId() : null);
        diveraSyncService.deleteEvent(unitId, reservation.getDiveraEventId(), actorUserId);
        googleCalendarService.deleteReservationCalendarEvent(ReservationKind.VEHICLE, reservation.getId());
    }

    private void cleanupRoomReservation(RoomReservation reservation) {
        long unitId = reservation.getUnit().getId();
        Long actorUserId = reservation.getApprovedByUser() != null
                ? reservation.getApprovedByUser().getId()
                : (reservation.getRequesterUser() != null ? reservation.getRequesterUser().getId() : null);
        diveraSyncService.deleteEvent(unitId, reservation.getDiveraEventId(), actorUserId);
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
                reservation.vehicleNamesJoined(),
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

    private static List<Long> resolveResourceIds(CreateReservationRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (request.resourceIds() != null) {
            for (Long id : request.resourceIds()) {
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty() && request.resourceId() != null && request.resourceId() > 0) {
            ids.add(request.resourceId());
        }
        return List.copyOf(ids);
    }

    private static List<CreateReservationRequest.ReservationTimeSlot> resolveSlots(CreateReservationRequest request) {
        List<CreateReservationRequest.ReservationTimeSlot> slots = new ArrayList<>();
        if (request.slots() != null) {
            for (CreateReservationRequest.ReservationTimeSlot slot : request.slots()) {
                if (slot != null && slot.startAt() != null && slot.endAt() != null) {
                    slots.add(slot);
                }
            }
        }
        if (slots.isEmpty() && request.startAt() != null && request.endAt() != null) {
            slots.add(new CreateReservationRequest.ReservationTimeSlot(request.startAt(), request.endAt()));
        }
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Termin angeben.");
        }
        return slots;
    }

    private static List<ReservationConflictView> distinctConflicts(List<ReservationConflictView> conflicts) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<ReservationConflictView> result = new ArrayList<>();
        for (ReservationConflictView conflict : conflicts) {
            if (conflict == null || !seen.add(conflict.id())) {
                continue;
            }
            result.add(conflict);
        }
        return result;
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
