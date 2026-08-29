package de.feuerwehr.manager.reservierungen;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.settings.TestModeService;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
    private final PersonRepository personRepository;
    private final UnitAdminService unitAdminService;
    private final ReservierungenSettingsService settingsService;
    private final ReservierungenConflictService conflictService;
    private final ReservierungenNotificationService notificationService;
    private final ReservierungenDiveraSyncService diveraSyncService;
    private final ReservierungenGoogleCalendarService googleCalendarService;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public List<ReservationListItemView> listMine(long unitId, long userId) {
        List<VehicleReservation> vehicles = new ArrayList<>();
        for (VehicleReservation reservation :
                vehicleReservationRepository.findByUnitIdAndRequesterUserIdOrderByStartAtDesc(unitId, userId)) {
            if (isVisible(reservation.isTestData())) {
                vehicles.add(reservation);
            }
        }
        List<RoomReservation> rooms = new ArrayList<>();
        for (RoomReservation reservation :
                roomReservationRepository.findByUnitIdAndRequesterUserIdOrderByStartAtDesc(unitId, userId)) {
            if (isVisible(reservation.isTestData())) {
                rooms.add(reservation);
            }
        }
        Map<Long, String> personNames = personNamesByUserId(vehicles, rooms);
        List<ReservationListItemView> items = new ArrayList<>();
        for (VehicleReservation reservation : vehicles) {
            items.add(toView(reservation, userId, false, personNames));
        }
        for (RoomReservation reservation : rooms) {
            items.add(toView(reservation, userId, false, personNames));
        }
        return upcomingSorted(items);
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemView> listPending(long unitId, long currentUserId) {
        List<VehicleReservation> vehicles = new ArrayList<>();
        for (VehicleReservation reservation :
                vehicleReservationRepository.findByUnitIdAndStatusOrderByStartAtAsc(unitId, ReservationStatus.PENDING)) {
            if (isVisible(reservation.isTestData())) {
                vehicles.add(reservation);
            }
        }
        List<RoomReservation> rooms = new ArrayList<>();
        for (RoomReservation reservation :
                roomReservationRepository.findByUnitIdAndStatusOrderByStartAtAsc(unitId, ReservationStatus.PENDING)) {
            if (isVisible(reservation.isTestData())) {
                rooms.add(reservation);
            }
        }
        Map<Long, String> personNames = personNamesByUserId(vehicles, rooms);
        List<ReservationListItemView> items = new ArrayList<>();
        for (VehicleReservation reservation : vehicles) {
            items.add(toView(reservation, currentUserId, hasVehicleConflict(reservation), personNames));
        }
        for (RoomReservation reservation : rooms) {
            items.add(toView(reservation, currentUserId, hasRoomConflict(reservation), personNames));
        }
        return upcomingSorted(items);
    }

    @Transactional(readOnly = true)
    public List<ReservationListItemView> listAll(long unitId, long currentUserId) {
        List<VehicleReservation> vehicles = new ArrayList<>();
        for (VehicleReservation reservation : vehicleReservationRepository.findByUnitIdOrderByStartAtDesc(unitId)) {
            if (isVisible(reservation.isTestData())) {
                vehicles.add(reservation);
            }
        }
        List<RoomReservation> rooms = new ArrayList<>();
        for (RoomReservation reservation : roomReservationRepository.findByUnitIdOrderByStartAtDesc(unitId)) {
            if (isVisible(reservation.isTestData())) {
                rooms.add(reservation);
            }
        }
        Map<Long, String> personNames = personNamesByUserId(vehicles, rooms);
        List<ReservationListItemView> items = new ArrayList<>();
        for (VehicleReservation reservation : vehicles) {
            items.add(toView(reservation, currentUserId, hasVehicleConflict(reservation), personNames));
        }
        for (RoomReservation reservation : rooms) {
            items.add(toView(reservation, currentUserId, hasRoomConflict(reservation), personNames));
        }
        return upcomingSorted(items);
    }

    @Transactional
    public List<VehicleReservation> createVehicleReservation(long unitId, Long userId, CreateReservationRequest request) {
        List<Long> vehicleIds = resolveResourceIds(request);
        List<CreateReservationRequest.ReservationTimeSlot> slots = resolveSlots(request);
        if (vehicleIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Fahrzeug wählen.");
        }
        String requesterEmail = requireText(request.requesterEmail(), "E-Mail");
        String reason = requireText(request.reason(), "Grund");
        String location = requireText(request.location(), "Ort / Standort");
        Unit unit = requireUnit(unitId);
        User requester = userId == null ? null : requireUser(userId);
        String requesterName = resolveStoredRequesterName(requester, requireText(request.requesterName(), "Antragsteller"));

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
        LinkedHashSet<Long> conflictingRequestedIds = new LinkedHashSet<>();
        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            validateTimes(slot.startAt(), slot.endAt());
            validateNotInPast(slot.startAt());
            for (Long vehicleId : vehicleIds) {
                List<ReservationConflictView> conflicts = conflictService.vehicleConflicts(
                        vehicleId, slot.startAt(), slot.endAt(), (Long) null);
                if (!conflicts.isEmpty()) {
                    conflictingRequestedIds.add(vehicleId);
                    allConflicts.addAll(conflicts);
                }
            }
        }
        if (!allConflicts.isEmpty() && !request.forceConflictOverride()) {
            throw new ReservationConflictException(
                    "Mindestens ein Fahrzeug ist in einem der Zeiträume bereits vergeben oder beantragt.",
                    distinctConflicts(allConflicts),
                    List.copyOf(conflictingRequestedIds));
        }

        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                    unitId, vehicleIds, slot.startAt(), slot.endAt(), (Long) null);
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
            reservation.setTestData(testModeService.testDataScope());
            saved.add(vehicleReservationRepository.save(reservation));
        }
        if (!saved.isEmpty()) {
            notificationService.notifyAdminsNewVehicleReservation(unitId, saved.get(0), saved.size());
        }
        return saved;
    }

    @Transactional
    public List<RoomReservation> createRoomReservation(long unitId, Long userId, CreateReservationRequest request) {
        List<Long> roomIds = resolveResourceIds(request);
        List<CreateReservationRequest.ReservationTimeSlot> slots = resolveSlots(request);
        if (roomIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Raum wählen.");
        }
        String requesterEmail = requireText(request.requesterEmail(), "E-Mail");
        String reason = requireText(request.reason(), "Grund");
        String location = requireText(request.location(), "Ort / Standort");
        Unit unit = requireUnit(unitId);
        User requester = userId == null ? null : requireUser(userId);
        String requesterName = resolveStoredRequesterName(requester, requireText(request.requesterName(), "Antragsteller"));

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
        LinkedHashSet<Long> conflictingRequestedIds = new LinkedHashSet<>();
        for (CreateReservationRequest.ReservationTimeSlot slot : slots) {
            validateTimes(slot.startAt(), slot.endAt());
            validateNotInPast(slot.startAt());
            for (Room room : rooms) {
                List<ReservationConflictView> conflicts =
                        conflictService.roomConflicts(room.getId(), slot.startAt(), slot.endAt(), null);
                if (!conflicts.isEmpty()) {
                    conflictingRequestedIds.add(room.getId());
                    allConflicts.addAll(conflicts);
                }
            }
        }
        if (!allConflicts.isEmpty() && !request.forceConflictOverride()) {
            throw new ReservationConflictException(
                    "Mindestens ein Raum ist in einem der Zeiträume bereits vergeben oder beantragt.",
                    distinctConflicts(allConflicts),
                    List.copyOf(conflictingRequestedIds));
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
                reservation.setTestData(testModeService.testDataScope());
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
        ResolvedRequester requester = resolveRequester(unitId, requesterName, requesterEmail, actor);
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
            reservation.setRequesterUser(requester.user());
            reservation.setRequesterName(requester.name());
            reservation.setRequesterEmail(requester.email());
            reservation.setReason(reason);
            reservation.setLocation(location);
            reservation.setStartAt(request.startAt());
            reservation.setEndAt(request.endAt());
            reservation.setStatus(ReservationStatus.APPROVED);
            reservation.setApprovedByUser(actor);
            reservation.setApprovedAt(Instant.now());
            reservation.setTestData(testModeService.testDataScope());
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
        reservation.setRequesterUser(requester.user());
        reservation.setRequesterName(requester.name());
        reservation.setRequesterEmail(requester.email());
        reservation.setReason(reason);
        reservation.setLocation(location);
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(actor);
        reservation.setApprovedAt(Instant.now());
        reservation.setTestData(testModeService.testDataScope());
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

    @Transactional
    public List<String> updateVehicleReservation(
            long unitId, long reservationId, long actorUserId, UpdateReservationRequest request) {
        VehicleReservation reservation = requireApprovedVehicle(unitId, reservationId);
        List<Long> vehicleIds = resolveResourceIds(request.resourceIds(), request.resourceId());
        if (vehicleIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens ein Fahrzeug wählen.");
        }
        validateTimes(request.startAt(), request.endAt());
        User actor = requireUser(actorUserId);
        ResolvedRequester requester = resolveRequester(
                unitId,
                requireText(request.requesterName(), "Antragsteller"),
                requireText(request.requesterEmail(), "E-Mail"),
                actor);

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

        List<ReservationConflictView> conflicts = conflictService.vehicleConflictsForVehicles(
                vehicleIds, request.startAt(), request.endAt(), reservation.getId());
        if (!conflicts.isEmpty() && !request.forceConflictOverride()) {
            throw new ReservationConflictException(
                    "Mindestens ein Fahrzeug ist in diesem Zeitraum bereits vergeben oder beantragt.",
                    distinctConflicts(conflicts),
                    vehicleIds);
        }
        LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                unitId, vehicleIds, request.startAt(), request.endAt(), reservation.getId());
        if (warning.warning() && !request.forceAvailabilityOverride()) {
            throw new LoeschfahrzeugWarningException(warning);
        }

        reservation.setVehiclesOrdered(vehicles);
        reservation.setRequesterUser(requester.user());
        reservation.setRequesterName(requester.name());
        reservation.setRequesterEmail(requester.email());
        reservation.setReason(requireText(request.reason(), "Grund"));
        reservation.setLocation(requireText(request.location(), "Ort / Standort"));
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        VehicleReservation saved = vehicleReservationRepository.save(reservation);
        List<String> notes = new ArrayList<>(applyVehicleIntegrations(unitId, saved, actorUserId, null));
        if (request.sendRequesterEmail()) {
            notificationService.notifyRequesterUpdated(unitId, saved);
            notes.add("Antragsteller per E-Mail über die Änderungen informiert.");
        }
        return notes;
    }

    @Transactional
    public List<String> updateRoomReservation(
            long unitId, long reservationId, long actorUserId, UpdateReservationRequest request) {
        RoomReservation reservation = requireApprovedRoom(unitId, reservationId);
        List<Long> roomIds = resolveResourceIds(request.resourceIds(), request.resourceId());
        if (roomIds.size() != 1) {
            throw new IllegalArgumentException("Bitte genau einen Raum wählen.");
        }
        validateTimes(request.startAt(), request.endAt());
        User actor = requireUser(actorUserId);
        ResolvedRequester requester = resolveRequester(
                unitId,
                requireText(request.requesterName(), "Antragsteller"),
                requireText(request.requesterEmail(), "E-Mail"),
                actor);
        Room room = roomRepository
                .findByIdAndUnitId(roomIds.get(0), unitId)
                .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        if (!room.isActive()) {
            throw new IllegalArgumentException("Raum \"" + room.getName() + "\" ist nicht aktiv.");
        }
        List<ReservationConflictView> conflicts =
                conflictService.roomConflicts(room.getId(), request.startAt(), request.endAt(), reservation.getId());
        if (!conflicts.isEmpty() && !request.forceConflictOverride()) {
            throw new ReservationConflictException(
                    "Der Raum ist in diesem Zeitraum bereits vergeben oder beantragt.", conflicts);
        }

        reservation.setRoom(room);
        reservation.setRequesterUser(requester.user());
        reservation.setRequesterName(requester.name());
        reservation.setRequesterEmail(requester.email());
        reservation.setReason(requireText(request.reason(), "Grund"));
        reservation.setLocation(requireText(request.location(), "Ort / Standort"));
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        RoomReservation saved = roomReservationRepository.save(reservation);
        List<String> notes = new ArrayList<>(applyRoomIntegrations(unitId, saved, actorUserId));
        if (request.sendRequesterEmail()) {
            notificationService.notifyRequesterUpdated(unitId, saved);
            notes.add("Antragsteller per E-Mail über die Änderungen informiert.");
        }
        return notes;
    }

    /**
     * Ordnet Antragsteller anhand der hinterlegten E-Mail dem passenden Benutzerkonto zu
     * (Import hat bisher oft den eingeloggten Bearbeiter gespeichert).
     */
    @Transactional
    public void relinkRequestersByEmail(long unitId) {
        for (VehicleReservation reservation : vehicleReservationRepository.findByUnitIdOrderByStartAtDesc(unitId)) {
            if (!isVisible(reservation.isTestData())) {
                continue;
            }
            relinkVehicleRequester(unitId, reservation);
        }
        for (RoomReservation reservation : roomReservationRepository.findByUnitIdOrderByStartAtDesc(unitId)) {
            if (!isVisible(reservation.isTestData())) {
                continue;
            }
            relinkRoomRequester(unitId, reservation);
        }
    }

    /**
     * Übernahme genehmigter Reservierungen aus JSON-Export der alten feuerwehr-app.
     * Kein DIVERA-/Google-Kalender-Sync und keine E-Mails.
     */
    @Transactional
    public LegacyReservationImportOutcome importLegacyExportFile(
            long unitId, long actorUserId, LegacyReservationExportFile exportFile) {
        if (exportFile == null || exportFile.reservations() == null || exportFile.reservations().isEmpty()) {
            throw new IllegalArgumentException("Die Export-Datei enthält keine Reservierungen.");
        }
        if (exportFile.formatVersion() != 1) {
            throw new IllegalArgumentException("Unbekannte Export-Version: " + exportFile.formatVersion());
        }
        Unit unit = requireUnit(unitId);
        User actor = requireUser(actorUserId);
        int imported = 0;
        int skipped = 0;
        List<String> details = new ArrayList<>();

        for (LegacyReservationExportItem item : exportFile.reservations()) {
            String label = describeLegacyItem(item);
            try {
                if (item == null) {
                    skipped++;
                    details.add("Leerer Eintrag übersprungen.");
                    continue;
                }
                if (item.status() != null && !"approved".equalsIgnoreCase(item.status().trim())) {
                    skipped++;
                    details.add(label + ": Status nicht genehmigt, übersprungen.");
                    continue;
                }
                String kind = item.kind() == null ? "" : item.kind().trim().toLowerCase(Locale.ROOT);
                boolean vehicleKind = "vehicle".equals(kind) || "fahrzeug".equals(kind);
                boolean roomKind = "room".equals(kind) || "raum".equals(kind);
                if (!vehicleKind && !roomKind) {
                    skipped++;
                    details.add(label + ": Unbekannte Art \"" + item.kind() + "\".");
                    continue;
                }
                String resourceName = requireText(item.resourceName(), "Ressource");
                Instant startAt = parseExportInstant(item.startAt());
                Instant endAt = parseExportInstant(item.endAt());
                validateTimes(startAt, endAt);
                String requesterName = requireText(item.requesterName(), "Antragsteller");
                String requesterEmail = requireText(item.requesterEmail(), "E-Mail");
                String reason = optionalText(item.reason(), "Übernommen aus feuerwehr-app");
                String location = optionalText(item.location(), "—");
                Instant approvedAt = parseExportInstant(item.approvedAt());
                if (approvedAt == null) {
                    approvedAt = Instant.now();
                }

                if (vehicleKind) {
                    if (isDuplicateVehicleImport(unitId, resourceName, startAt, endAt, requesterEmail)) {
                        skipped++;
                        details.add(label + ": Bereits vorhanden, übersprungen.");
                        continue;
                    }
                    Vehicle vehicle = findActiveVehicleByName(unitId, resourceName)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Fahrzeug \"" + resourceName + "\" nicht gefunden."));
                    VehicleReservation reservation = new VehicleReservation();
                    reservation.setUnit(unit);
                    reservation.setVehiclesOrdered(List.of(vehicle));
                    ResolvedRequester requester = resolveRequester(unitId, requesterName, requesterEmail, actor);
                    reservation.setRequesterUser(requester.user());
                    reservation.setRequesterName(requester.name());
                    reservation.setRequesterEmail(requester.email());
                    reservation.setReason(reason);
                    reservation.setLocation(location);
                    reservation.setStartAt(startAt);
                    reservation.setEndAt(endAt);
                    reservation.setStatus(ReservationStatus.APPROVED);
                    reservation.setApprovedByUser(actor);
                    reservation.setApprovedAt(approvedAt);
                    reservation.setTestData(testModeService.testDataScope());
                    vehicleReservationRepository.save(reservation);
                } else {
                    if (isDuplicateRoomImport(unitId, resourceName, startAt, endAt, requesterEmail)) {
                        skipped++;
                        details.add(label + ": Bereits vorhanden, übersprungen.");
                        continue;
                    }
                    Room room = findActiveRoomByName(unitId, resourceName)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Raum \"" + resourceName + "\" nicht gefunden."));
                    RoomReservation reservation = new RoomReservation();
                    reservation.setUnit(unit);
                    reservation.setRoom(room);
                    ResolvedRequester requester = resolveRequester(unitId, requesterName, requesterEmail, actor);
                    reservation.setRequesterUser(requester.user());
                    reservation.setRequesterName(requester.name());
                    reservation.setRequesterEmail(requester.email());
                    reservation.setReason(reason);
                    reservation.setLocation(location);
                    reservation.setStartAt(startAt);
                    reservation.setEndAt(endAt);
                    reservation.setStatus(ReservationStatus.APPROVED);
                    reservation.setApprovedByUser(actor);
                    reservation.setApprovedAt(approvedAt);
                    reservation.setTestData(testModeService.testDataScope());
                    roomReservationRepository.save(reservation);
                }
                imported++;
                details.add(label + ": Importiert.");
            } catch (RuntimeException e) {
                skipped++;
                details.add(label + ": " + e.getMessage());
            }
        }
        return new LegacyReservationImportOutcome(imported, skipped, List.copyOf(details));
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> checkVehicleConflicts(long unitId, long reservationId) {
        VehicleReservation reservation = vehicleReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .filter(r -> isVisible(r.isTestData()))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        List<Long> vehicleIds = reservation.resolvedVehicles().stream().map(Vehicle::getId).toList();
        return conflictService.vehicleConflictsForVehicles(
                vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
    }

    @Transactional(readOnly = true)
    public List<ReservationConflictView> checkRoomConflicts(long unitId, long reservationId) {
        RoomReservation reservation = roomReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .filter(r -> isVisible(r.isTestData()))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
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
    public void deleteVehicleReservation(long unitId, long reservationId, String deletionReason) {
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
                            : "Ihr Antrag auf eine Fahrzeugreservierung wurde gelöscht.",
                    deletionReason);
        }
    }

    @Transactional
    public void deleteRoomReservation(long unitId, long reservationId, String deletionReason) {
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
                            : "Ihr Antrag auf eine Raumreservierung wurde gelöscht.",
                    deletionReason);
        }
    }

    /** Beim Beenden des Testmodus: externe Termine entfernen und Testdaten löschen. */
    @Transactional
    public void purgeAllTestData() {
        for (VehicleReservation reservation : vehicleReservationRepository.findByTestDataTrue()) {
            cleanupVehicleReservation(reservation);
            vehicleReservationRepository.delete(reservation);
        }
        for (RoomReservation reservation : roomReservationRepository.findByTestDataTrue()) {
            cleanupRoomReservation(reservation);
            roomReservationRepository.delete(reservation);
        }
    }

    private List<String> approveVehicle(VehicleReservation reservation, long actorUserId, ProcessReservationRequest request) {
        long unitId = reservation.getUnit().getId();
        List<Vehicle> allVehicles = reservation.resolvedVehicles();
        List<Long> allIds = allVehicles.stream().map(Vehicle::getId).toList();

        LinkedHashSet<Long> approvedIds = new LinkedHashSet<>();
        LinkedHashSet<Long> rejectedIds = new LinkedHashSet<>();
        if (request.approvedVehicleIds() != null && !request.approvedVehicleIds().isEmpty()) {
            for (Long id : request.approvedVehicleIds()) {
                if (id != null && allIds.contains(id)) {
                    approvedIds.add(id);
                }
            }
            for (Long id : allIds) {
                if (!approvedIds.contains(id)) {
                    rejectedIds.add(id);
                }
            }
        } else if (request.rejectedVehicleIds() != null && !request.rejectedVehicleIds().isEmpty()) {
            for (Long id : request.rejectedVehicleIds()) {
                if (id != null && allIds.contains(id)) {
                    rejectedIds.add(id);
                }
            }
            for (Long id : allIds) {
                if (!rejectedIds.contains(id)) {
                    approvedIds.add(id);
                }
            }
        } else {
            approvedIds.addAll(allIds);
        }

        if (approvedIds.isEmpty()) {
            rejectVehicle(reservation, actorUserId, request.reason());
            return List.of();
        }

        List<Vehicle> approvedVehicles = allVehicles.stream()
                .filter(v -> approvedIds.contains(v.getId()))
                .toList();
        List<Long> vehicleIds = approvedVehicles.stream().map(Vehicle::getId).toList();

        boolean resolveConflicts = "approve_with_conflict_resolution".equals(normalizeAction(request.action()));
        List<ReservationConflictView> conflicts = conflictService.vehicleConflictsForVehicles(
                vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
        if (!conflicts.isEmpty() && !resolveConflicts) {
            throw new ReservationConflictException(
                    "Das Fahrzeug ist in diesem Zeitraum bereits genehmigt belegt oder beantragt.",
                    conflicts,
                    vehicleIds);
        }

        LinkedHashSet<Long> loeschExcludes = new LinkedHashSet<>();
        loeschExcludes.add(reservation.getId());
        if (resolveConflicts && request.conflictIds() != null) {
            loeschExcludes.addAll(request.conflictIds());
        }
        LoeschfahrzeugWarningView warning = conflictService.checkLoeschfahrzeugWarning(
                unitId, vehicleIds, reservation.getStartAt(), reservation.getEndAt(), loeschExcludes);
        if (warning.warning() && !request.forceAvailabilityOverride()) {
            throw new LoeschfahrzeugWarningException(warning);
        }

        if (resolveConflicts) {
            conflicts = conflictService.vehicleConflictsForVehicles(
                    vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId());
            cancelVehicleConflicts(unitId, conflicts, request.conflictIds());
        }

        if (!rejectedIds.isEmpty()) {
            List<Vehicle> rejectedVehicles = allVehicles.stream()
                    .filter(v -> rejectedIds.contains(v.getId()))
                    .toList();
            createRejectedVehicleSibling(reservation, rejectedVehicles, actorUserId, request.reason());
            reservation.setVehiclesOrdered(new ArrayList<>(approvedVehicles));
        }

        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setApprovedByUser(requireUser(actorUserId));
        reservation.setApprovedAt(Instant.now());
        vehicleReservationRepository.save(reservation);
        List<String> syncNotes = applyVehicleIntegrations(unitId, reservation, actorUserId, request.diveraGroupIds());
        if (rejectedIds.isEmpty()) {
            notificationService.notifyRequesterDecision(unitId, reservation, true, null);
        } else {
            String rejectedNames = allVehicles.stream()
                    .filter(v -> rejectedIds.contains(v.getId()))
                    .map(Vehicle::getName)
                    .collect(Collectors.joining(", "));
            notificationService.notifyRequesterPartialVehicleDecision(
                    unitId,
                    reservation,
                    reservation.vehicleNamesJoined(),
                    rejectedNames,
                    request.reason());
        }
        return syncNotes;
    }

    private void createRejectedVehicleSibling(
            VehicleReservation source, List<Vehicle> rejectedVehicles, long actorUserId, String reason) {
        if (rejectedVehicles == null || rejectedVehicles.isEmpty()) {
            return;
        }
        VehicleReservation rejected = new VehicleReservation();
        rejected.setUnit(source.getUnit());
        rejected.setVehiclesOrdered(new ArrayList<>(rejectedVehicles));
        rejected.setRequesterUser(source.getRequesterUser());
        rejected.setRequesterName(source.getRequesterName());
        rejected.setRequesterEmail(source.getRequesterEmail());
        rejected.setReason(source.getReason());
        rejected.setLocation(source.getLocation());
        rejected.setStartAt(source.getStartAt());
        rejected.setEndAt(source.getEndAt());
        rejected.setStatus(ReservationStatus.REJECTED);
        rejected.setRejectionReason(trimToNull(reason));
        rejected.setApprovedByUser(requireUser(actorUserId));
        rejected.setApprovedAt(Instant.now());
        rejected.setTestData(source.isTestData());
        vehicleReservationRepository.save(rejected);
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
            if (existing == null
                    || (existing.getStatus() != ReservationStatus.APPROVED
                            && existing.getStatus() != ReservationStatus.PENDING)) {
                continue;
            }
            boolean wasApproved = existing.getStatus() == ReservationStatus.APPROVED;
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
                    wasApproved
                            ? "Ihre genehmigte Fahrzeugreservierung wurde wegen eines Konflikts storniert."
                            : "Ihr Antrag auf eine Fahrzeugreservierung wurde wegen eines Konflikts storniert.",
                    null);
        }
    }

    private void cancelRoomConflicts(long unitId, List<ReservationConflictView> conflicts, List<Long> conflictIds) {
        for (ReservationConflictView conflict : conflicts) {
            if (conflictIds != null && !conflictIds.isEmpty() && !conflictIds.contains(conflict.id())) {
                continue;
            }
            RoomReservation existing = roomReservationRepository.findById(conflict.id()).orElse(null);
            if (existing == null
                    || (existing.getStatus() != ReservationStatus.APPROVED
                            && existing.getStatus() != ReservationStatus.PENDING)) {
                continue;
            }
            boolean wasApproved = existing.getStatus() == ReservationStatus.APPROVED;
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
                    wasApproved
                            ? "Ihre genehmigte Raumreservierung wurde wegen eines Konflikts storniert."
                            : "Ihr Antrag auf eine Raumreservierung wurde wegen eines Konflikts storniert.",
                    null);
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
        List<VehicleReservation> siblings = findApprovedVehicleSlotSiblings(reservation);
        List<VehicleReservation> group = new ArrayList<>(siblings);
        group.add(reservation);
        String combinedNames = combinedVehicleNames(group);
        Long existingDiveraEventId = reservation.getDiveraEventId();
        if (existingDiveraEventId == null || existingDiveraEventId <= 0) {
            existingDiveraEventId = siblings.stream()
                    .map(VehicleReservation::getDiveraEventId)
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .findFirst()
                    .orElse(null);
        }
        Map<Long, String> existingGoogleByAccount = new LinkedHashMap<>();
        existingGoogleByAccount.putAll(
                googleCalendarService.googleEventIdsByAccount(ReservationKind.VEHICLE, reservation.getId()));
        for (VehicleReservation sibling : siblings) {
            existingGoogleByAccount.putAll(
                    googleCalendarService.googleEventIdsByAccount(ReservationKind.VEHICLE, sibling.getId()));
        }
        boolean hadOwnDivera = reservation.getDiveraEventId() != null && reservation.getDiveraEventId() > 0;
        boolean hadOwnGoogle = !googleCalendarService
                .googleEventIdsByAccount(ReservationKind.VEHICLE, reservation.getId())
                .isEmpty();
        boolean merged = !siblings.isEmpty() && (existingDiveraEventId != null || !existingGoogleByAccount.isEmpty());

        if (settings.isVehicleDiveraEnabled()) {
            List<Integer> groups = diveraGroupIds != null && !diveraGroupIds.isEmpty()
                    ? diveraGroupIds
                    : settingsService.defaultDiveraGroupIds(settings, false);
            var synced = diveraSyncService.syncVehicleReservation(
                    reservation, groups, actorUserId, combinedNames, existingDiveraEventId);
            if (synced.isPresent()) {
                reservation.setDiveraEventId(synced.get());
                vehicleReservationRepository.save(reservation);
                notes.add(
                        hadOwnDivera
                                ? "DIVERA: Termin aktualisiert."
                                : (merged && existingDiveraEventId != null
                                        ? "DIVERA: Fahrzeug dem bestehenden Termin hinzugefügt."
                                        : "DIVERA: Termin angelegt."));
            } else {
                notes.add(
                        "DIVERA: Termin konnte nicht angelegt werden"
                                + " (persönlichen Access Key unter Einstellungen bzw."
                                + " Einheits-Key unter Admin → Schnittstellen prüfen; Server-Log).");
            }
        }
        if (settings.isVehicleGoogleCalendarEnabled()) {
            var googleSync = googleCalendarService.syncVehicleReservation(
                    unitId,
                    reservation,
                    settingsService.vehicleGoogleCalendarAccountIds(settings),
                    combinedNames,
                    existingGoogleByAccount);
            if (googleSync.ok()) {
                notes.add(
                        hadOwnGoogle
                                ? "Google Kalender: Termin aktualisiert."
                                : (merged && !existingGoogleByAccount.isEmpty()
                                        ? "Google Kalender: Fahrzeug dem bestehenden Termin hinzugefügt."
                                        : "Google Kalender: "
                                                + googleSync.synced()
                                                + (googleSync.synced() == 1 ? " Termin" : " Termine")
                                                + " angelegt."));
            } else {
                String detail = googleSync.primaryError();
                notes.add(
                        "Google Kalender: kein Termin angelegt"
                                + (detail != null && !detail.isBlank() ? " – " + detail : ".")
                                + " (Admin → Schnittstellen → Kalender prüfen).");
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
            Long previousDiveraId = reservation.getDiveraEventId();
            var synced = diveraSyncService.syncRoomReservation(reservation, groups, actorUserId);
            if (synced.isPresent()) {
                reservation.setDiveraEventId(synced.get());
                roomReservationRepository.save(reservation);
                notes.add(
                        previousDiveraId != null && previousDiveraId > 0
                                ? "DIVERA: Termin aktualisiert."
                                : "DIVERA: Termin angelegt.");
            } else {
                notes.add(
                        "DIVERA: Termin konnte nicht angelegt werden"
                                + " (persönlichen Access Key unter Einstellungen bzw."
                                + " Einheits-Key unter Admin → Schnittstellen prüfen; Server-Log).");
            }
        }
        if (settings.isRoomGoogleCalendarEnabled()) {
            var googleSync = googleCalendarService.syncRoomReservation(
                    unitId, reservation, settingsService.roomGoogleCalendarAccountIds(settings));
            if (googleSync.ok()) {
                notes.add(
                        "Google Kalender: "
                                + googleSync.synced()
                                + (googleSync.synced() == 1 ? " Termin" : " Termine")
                                + " angelegt.");
            } else {
                String detail = googleSync.primaryError();
                notes.add(
                        "Google Kalender: kein Termin angelegt"
                                + (detail != null && !detail.isBlank() ? " – " + detail : ".")
                                + " (Admin → Schnittstellen → Kalender prüfen).");
            }
        }
        return notes;
    }

    private void cleanupVehicleReservation(VehicleReservation reservation) {
        long unitId = reservation.getUnit().getId();
        Long actorUserId = reservation.getApprovedByUser() != null
                ? reservation.getApprovedByUser().getId()
                : (reservation.getRequesterUser() != null ? reservation.getRequesterUser().getId() : null);
        List<VehicleReservation> remaining = remainingApprovedVehicleCalendarSiblings(reservation);
        String combinedNames = combinedVehicleNames(remaining);
        Long diveraEventId = reservation.getDiveraEventId();

        if (diveraEventId != null && diveraEventId > 0) {
            if (remaining.isEmpty()) {
                diveraSyncService.deleteEvent(unitId, diveraEventId, actorUserId);
            } else {
                UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
                List<Integer> groups = settingsService.defaultDiveraGroupIds(settings, false);
                VehicleReservation primary = remaining.get(0);
                diveraSyncService.updateVehicleEvent(
                        unitId,
                        diveraEventId,
                        primary.getId(),
                        combinedNames,
                        primary.getReason(),
                        primary.getLocation(),
                        primary.getStartAt(),
                        primary.getEndAt(),
                        groups,
                        actorUserId);
            }
            reservation.setDiveraEventId(null);
        }

        if (remaining.isEmpty()) {
            googleCalendarService.deleteReservationCalendarEvent(ReservationKind.VEHICLE, reservation.getId(), true);
        } else {
            UnitReservierungenSettings settings = settingsService.ensureSettings(unitId);
            Map<Long, String> googleIds = new LinkedHashMap<>();
            googleIds.putAll(
                    googleCalendarService.googleEventIdsByAccount(ReservationKind.VEHICLE, reservation.getId()));
            for (VehicleReservation sibling : remaining) {
                googleIds.putAll(
                        googleCalendarService.googleEventIdsByAccount(ReservationKind.VEHICLE, sibling.getId()));
            }
            VehicleReservation primary = remaining.get(0);
            if (!googleIds.isEmpty()) {
                googleCalendarService.syncVehicleReservation(
                        unitId,
                        primary,
                        settingsService.vehicleGoogleCalendarAccountIds(settings),
                        combinedNames,
                        googleIds);
            }
            googleCalendarService.deleteReservationCalendarEvent(ReservationKind.VEHICLE, reservation.getId(), false);
        }
    }

    private void cleanupRoomReservation(RoomReservation reservation) {
        long unitId = reservation.getUnit().getId();
        Long actorUserId = reservation.getApprovedByUser() != null
                ? reservation.getApprovedByUser().getId()
                : (reservation.getRequesterUser() != null ? reservation.getRequesterUser().getId() : null);
        diveraSyncService.deleteEvent(unitId, reservation.getDiveraEventId(), actorUserId);
        googleCalendarService.deleteReservationCalendarEvent(ReservationKind.ROOM, reservation.getId());
    }

    /** Andere genehmigte Fahrzeugreservierungen mit gleichem Grund und Zeitraum. */
    private List<VehicleReservation> findApprovedVehicleSlotSiblings(VehicleReservation reservation) {
        String reason = reservation.getReason() != null ? reservation.getReason() : "";
        return vehicleReservationRepository.findApprovedSlotSiblings(
                reservation.getUnit().getId(),
                ReservationStatus.APPROVED,
                reservation.getStartAt(),
                reservation.getEndAt(),
                reason,
                reservation.getId());
    }

    /**
     * Verbleibende genehmigte Reservierungen, die denselben Kalendertermin teilen
     * (gleiche DIVERA-Event-ID oder gleicher Slot).
     */
    private List<VehicleReservation> remainingApprovedVehicleCalendarSiblings(VehicleReservation reservation) {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<VehicleReservation> result = new ArrayList<>();
        Long diveraEventId = reservation.getDiveraEventId();
        if (diveraEventId != null && diveraEventId > 0) {
            for (VehicleReservation sibling : vehicleReservationRepository.findByDiveraEventIdAndStatusAndIdNot(
                    diveraEventId, ReservationStatus.APPROVED, reservation.getId())) {
                if (seen.add(sibling.getId())) {
                    result.add(sibling);
                }
            }
        }
        for (VehicleReservation sibling : findApprovedVehicleSlotSiblings(reservation)) {
            if (seen.add(sibling.getId())) {
                result.add(sibling);
            }
        }
        result.sort(Comparator.comparing(VehicleReservation::getId));
        return result;
    }

    private static String combinedVehicleNames(List<VehicleReservation> reservations) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (VehicleReservation reservation : reservations) {
            for (Vehicle vehicle : reservation.resolvedVehicles()) {
                if (vehicle.getName() != null && !vehicle.getName().isBlank()) {
                    names.add(vehicle.getName().trim());
                }
            }
        }
        return String.join(", ", names);
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

    private VehicleReservation requireApprovedVehicle(long unitId, long reservationId) {
        VehicleReservation reservation = vehicleReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalArgumentException("Nur genehmigte Reservierungen können bearbeitet werden.");
        }
        return reservation;
    }

    private RoomReservation requireApprovedRoom(long unitId, long reservationId) {
        RoomReservation reservation = roomReservationRepository
                .findById(reservationId)
                .filter(r -> r.getUnit().getId().equals(unitId))
                .orElseThrow(() -> new IllegalArgumentException("Reservierung nicht gefunden."));
        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw new IllegalArgumentException("Nur genehmigte Reservierungen können bearbeitet werden.");
        }
        return reservation;
    }

    private ReservationListItemView toView(
            VehicleReservation reservation,
            long currentUserId,
            boolean hasConflict,
            Map<Long, String> personNamesByUserId) {
        List<ReservationResourceItem> resources = reservation.resolvedVehicles().stream()
                .map(v -> new ReservationResourceItem(v.getId(), v.getName()))
                .toList();
        return new ReservationListItemView(
                reservation.getId(),
                ReservationKind.VEHICLE,
                reservation.vehicleNamesJoined(),
                displayRequesterName(reservation.getRequesterUser(), reservation.getRequesterName(), personNamesByUserId),
                reservation.getRequesterEmail(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus(),
                reservation.getRejectionReason(),
                reservation.getApprovedAt(),
                reservation.getApprovedByUser() != null ? reservation.getApprovedByUser().getDisplayName() : null,
                reservation.getCreatedAt(),
                reservation.getRequesterUser() != null
                        && Objects.equals(reservation.getRequesterUser().getId(), currentUserId),
                hasConflict,
                resources);
    }

    private ReservationListItemView toView(
            RoomReservation reservation,
            long currentUserId,
            boolean hasConflict,
            Map<Long, String> personNamesByUserId) {
        List<ReservationResourceItem> resources = reservation.getRoom() != null
                ? List.of(new ReservationResourceItem(reservation.getRoom().getId(), reservation.getRoom().getName()))
                : List.of();
        return new ReservationListItemView(
                reservation.getId(),
                ReservationKind.ROOM,
                reservation.getRoom().getName(),
                displayRequesterName(reservation.getRequesterUser(), reservation.getRequesterName(), personNamesByUserId),
                reservation.getRequesterEmail(),
                reservation.getReason(),
                reservation.getLocation(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus(),
                reservation.getRejectionReason(),
                reservation.getApprovedAt(),
                reservation.getApprovedByUser() != null ? reservation.getApprovedByUser().getDisplayName() : null,
                reservation.getCreatedAt(),
                reservation.getRequesterUser() != null
                        && Objects.equals(reservation.getRequesterUser().getId(), currentUserId),
                hasConflict,
                resources);
    }

    /**
     * Nur aktuelle und kommende Reservierungen, nächster Zeitraum zuerst.
     * Abgelaufene Termine ({@code endAt} vor jetzt) erscheinen nicht in der Liste.
     */
    private static List<ReservationListItemView> upcomingSorted(List<ReservationListItemView> items) {
        Instant now = Instant.now();
        items.removeIf(item -> item.endAt() == null || item.endAt().isBefore(now));
        items.sort(Comparator.comparing(ReservationListItemView::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReservationListItemView::id));
        return items;
    }

    private Map<Long, String> personNamesByUserId(
            List<VehicleReservation> vehicles, List<RoomReservation> rooms) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        for (VehicleReservation reservation : vehicles) {
            if (reservation.getRequesterUser() != null && reservation.getRequesterUser().getId() != null) {
                userIds.add(reservation.getRequesterUser().getId());
            }
        }
        for (RoomReservation reservation : rooms) {
            if (reservation.getRequesterUser() != null && reservation.getRequesterUser().getId() != null) {
                userIds.add(reservation.getRequesterUser().getId());
            }
        }
        return loadPersonNamesByUserId(userIds);
    }

    private Map<Long, String> loadPersonNamesByUserId(LinkedHashSet<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (Person person : personRepository.findAllByUserIdIn(userIds)) {
            if (person.getUser() == null || person.getUser().getId() == null) {
                continue;
            }
            String fullName = personFullName(person);
            if (fullName != null) {
                names.putIfAbsent(person.getUser().getId(), fullName);
            }
        }
        return names;
    }

    private record ResolvedRequester(User user, String name, String email) {}

    private ResolvedRequester resolveRequester(long unitId, String submittedName, String submittedEmail, User importer) {
        String email = submittedEmail.trim();
        String name = submittedName.trim();
        User matched = findUserByRequesterEmail(unitId, email);
        if (matched != null) {
            String personName = lookupPersonFullName(matched.getId());
            if (personName != null && (matchesImporterName(importer, name) || !looksLikeFullName(name))) {
                name = personName;
            }
            return new ResolvedRequester(matched, name, email);
        }
        Person person = findPersonByRequesterEmail(unitId, email);
        if (person != null) {
            String personName = personFullName(person);
            if (personName != null && (matchesImporterName(importer, name) || !looksLikeFullName(name))) {
                name = personName;
            }
            User linked = person.getUser();
            if (linked != null && linked.getAnonymizedAt() != null) {
                linked = null;
            }
            return new ResolvedRequester(linked, name, email);
        }
        return new ResolvedRequester(null, name, email);
    }

    private User findUserByRequesterEmail(long unitId, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim();
        Person person = findPersonByRequesterEmail(unitId, normalized);
        if (person != null && person.getUser() != null && person.getUser().getAnonymizedAt() == null) {
            return person.getUser();
        }
        User byLogin = userRepository.findByLoginEmailIgnoreCaseWithUnit(normalized).orElse(null);
        if (byLogin != null && byLogin.getAnonymizedAt() == null) {
            return byLogin;
        }
        return userRepository.findByPersonEmailIgnoreCaseWithUnit(normalized).orElse(null);
    }

    private Person findPersonByRequesterEmail(long unitId, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        List<Person> matches = personRepository.findByUnitIdAndAnyEmailIgnoreCase(unitId, email.trim());
        for (Person person : matches) {
            if (person.getUser() != null && person.getUser().getAnonymizedAt() == null) {
                return person;
            }
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void relinkVehicleRequester(long unitId, VehicleReservation reservation) {
        if (reservation.getRequesterEmail() == null || reservation.getRequesterEmail().isBlank()) {
            return;
        }
        User matched = findUserByRequesterEmail(unitId, reservation.getRequesterEmail());
        if (matched == null) {
            return;
        }
        boolean userChanged = reservation.getRequesterUser() == null
                || !matched.getId().equals(reservation.getRequesterUser().getId());
        if (!userChanged) {
            return;
        }
        reservation.setRequesterUser(matched);
        String personName = lookupPersonFullName(matched.getId());
        if (personName != null) {
            reservation.setRequesterName(personName);
        }
        vehicleReservationRepository.save(reservation);
    }

    private void relinkRoomRequester(long unitId, RoomReservation reservation) {
        if (reservation.getRequesterEmail() == null || reservation.getRequesterEmail().isBlank()) {
            return;
        }
        User matched = findUserByRequesterEmail(unitId, reservation.getRequesterEmail());
        if (matched == null) {
            return;
        }
        boolean userChanged = reservation.getRequesterUser() == null
                || !matched.getId().equals(reservation.getRequesterUser().getId());
        if (!userChanged) {
            return;
        }
        reservation.setRequesterUser(matched);
        String personName = lookupPersonFullName(matched.getId());
        if (personName != null) {
            reservation.setRequesterName(personName);
        }
        roomReservationRepository.save(reservation);
    }

    private String resolveStoredRequesterName(User requester, String submitted) {
        if (requester == null || submitted == null) {
            return submitted;
        }
        if (!matchesAccountName(requester, submitted)) {
            return submitted;
        }
        String fromPerson = lookupPersonFullName(requester.getId());
        return fromPerson != null ? fromPerson : submitted;
    }

    private String displayRequesterName(
            User requesterUser, String storedName, Map<Long, String> personNamesByUserId) {
        if (storedName != null && looksLikeFullName(storedName)) {
            return storedName.trim();
        }
        if (requesterUser != null && requesterUser.getId() != null) {
            String fromPerson = personNamesByUserId.get(requesterUser.getId());
            if (fromPerson != null && !fromPerson.isBlank()) {
                return fromPerson;
            }
            String displayName = requesterUser.getDisplayName();
            if (displayName != null && looksLikeFullName(displayName)) {
                return displayName.trim();
            }
        }
        return storedName;
    }

    private String lookupPersonFullName(long userId) {
        return personRepository.findAllByUserIdAndAnonymizedAtIsNull(userId).stream()
                .map(ReservierungenService::personFullName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String personFullName(Person person) {
        String first = person.getFirstName() != null ? person.getFirstName().trim() : "";
        String last = person.getLastName() != null ? person.getLastName().trim() : "";
        if ("—".equals(last) || "-".equals(last) || "–".equals(last)) {
            last = "";
        }
        if ("—".equals(first) || "-".equals(first) || "–".equals(first)) {
            first = "";
        }
        if (last.isEmpty() && first.isEmpty()) {
            return null;
        }
        if (last.isEmpty()) {
            return looksLikeFullName(first) ? first : null;
        }
        if (first.isEmpty()) {
            return looksLikeFullName(last) ? last : null;
        }
        return last + ", " + first;
    }

    private static boolean matchesAccountName(User user, String submitted) {
        String name = submitted.trim();
        if (name.equalsIgnoreCase(user.getUsername())) {
            return true;
        }
        return user.getDisplayName() != null && name.equalsIgnoreCase(user.getDisplayName().trim());
    }

    private boolean matchesImporterName(User importer, String submitted) {
        if (importer == null || submitted == null || submitted.isBlank()) {
            return false;
        }
        if (matchesAccountName(importer, submitted)) {
            return true;
        }
        String personName = lookupPersonFullName(importer.getId());
        return personName != null && submitted.trim().equalsIgnoreCase(personName);
    }

    private static boolean looksLikeFullName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return trimmed.contains(" ") || trimmed.contains(",");
    }

    private boolean hasVehicleConflict(VehicleReservation reservation) {
        List<Long> vehicleIds = reservation.resolvedVehicles().stream().map(Vehicle::getId).toList();
        return !conflictService
                .vehicleConflictsForVehicles(
                        vehicleIds, reservation.getStartAt(), reservation.getEndAt(), reservation.getId())
                .isEmpty();
    }

    private boolean hasRoomConflict(RoomReservation reservation) {
        return !conflictService
                .roomConflicts(
                        reservation.getRoom().getId(),
                        reservation.getStartAt(),
                        reservation.getEndAt(),
                        reservation.getId())
                .isEmpty();
    }

    private boolean isVisible(boolean testData) {
        return !testData || testModeService.isEnabled();
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

    private static void validateNotInPast(Instant startAt) {
        if (startAt != null && startAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Der Beginn darf nicht in der Vergangenheit liegen.");
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

    private static List<Long> resolveResourceIds(List<Long> resourceIds, Long resourceId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (resourceIds != null) {
            for (Long id : resourceIds) {
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty() && resourceId != null && resourceId > 0) {
            ids.add(resourceId);
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

    private static String optionalText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static Instant parseExportInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ungültiges Datum: " + value);
        }
    }

    private static String normalizeResourceName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String describeLegacyItem(LegacyReservationExportItem item) {
        if (item == null) {
            return "Eintrag";
        }
        String kind = item.kind() == null ? "?" : item.kind();
        String resource = item.resourceName() == null ? "?" : item.resourceName();
        if (item.legacyId() != null) {
            return kind + " #" + item.legacyId() + " (" + resource + ")";
        }
        return kind + " (" + resource + ")";
    }

    private java.util.Optional<Vehicle> findActiveVehicleByName(long unitId, String name) {
        String needle = normalizeResourceName(name);
        return vehicleRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.testDataScope())
                .stream()
                .filter(Vehicle::isActive)
                .filter(vehicle -> normalizeResourceName(vehicle.getName()).equals(needle))
                .findFirst();
    }

    private java.util.Optional<Room> findActiveRoomByName(long unitId, String name) {
        String needle = normalizeResourceName(name);
        return roomRepository
                .findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.testDataScope())
                .stream()
                .filter(Room::isActive)
                .filter(room -> normalizeResourceName(room.getName()).equals(needle))
                .findFirst();
    }

    private boolean isDuplicateVehicleImport(
            long unitId, String resourceName, Instant startAt, Instant endAt, String requesterEmail) {
        String normEmail = normalizeResourceName(requesterEmail);
        String normResource = normalizeResourceName(resourceName);
        return vehicleReservationRepository
                .findByUnitIdAndStatusOrderByStartAtAsc(unitId, ReservationStatus.APPROVED)
                .stream()
                .filter(reservation -> isVisible(reservation.isTestData()))
                .anyMatch(reservation -> reservation.getStartAt().equals(startAt)
                        && reservation.getEndAt().equals(endAt)
                        && normalizeResourceName(reservation.getRequesterEmail()).equals(normEmail)
                        && reservation.resolvedVehicles().stream()
                                .anyMatch(vehicle -> normalizeResourceName(vehicle.getName()).equals(normResource)));
    }

    private boolean isDuplicateRoomImport(
            long unitId, String resourceName, Instant startAt, Instant endAt, String requesterEmail) {
        String normEmail = normalizeResourceName(requesterEmail);
        String normResource = normalizeResourceName(resourceName);
        return roomReservationRepository
                .findByUnitIdAndStatusOrderByStartAtAsc(unitId, ReservationStatus.APPROVED)
                .stream()
                .filter(reservation -> isVisible(reservation.isTestData()))
                .anyMatch(reservation -> reservation.getStartAt().equals(startAt)
                        && reservation.getEndAt().equals(endAt)
                        && normalizeResourceName(reservation.getRequesterEmail()).equals(normEmail)
                        && normalizeResourceName(reservation.getRoom().getName()).equals(normResource));
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
