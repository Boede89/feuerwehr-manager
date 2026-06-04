package de.feuerwehr.manager.unit;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Room;
import de.feuerwehr.manager.technik.RoomRepository;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleEquipment;
import de.feuerwehr.manager.technik.VehicleEquipmentCategory;
import de.feuerwehr.manager.technik.VehicleEquipmentCategoryRepository;
import de.feuerwehr.manager.technik.VehicleEquipmentRepository;
import de.feuerwehr.manager.technik.VehicleRepository;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UnitAdminService {

    private final UnitRepository unitRepository;
    private final UnitSmtpSettingsRepository smtpSettingsRepository;
    private final UnitCalendarSettingsRepository calendarSettingsRepository;
    private final VehicleRepository vehicleRepository;
    private final RoomRepository roomRepository;
    private final VehicleEquipmentCategoryRepository equipmentCategoryRepository;
    private final VehicleEquipmentRepository equipmentRepository;
    private final TestModeService testModeService;

    @Transactional
    public Unit saveStammdaten(long unitId, String name, String street, String postalCity) {
        Unit unit = requireUnit(unitId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen für die Einheit eingeben.");
        }
        unit.setName(name.trim());
        unit.setStreet(trimToNull(street));
        unit.setPostalCity(trimToNull(postalCity));
        return unitRepository.save(unit);
    }

    @Transactional
    public void saveLogo(long unitId, String contentType, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Bitte eine Bilddatei auswählen.");
        }
        if (imageBytes.length > 200_000) {
            throw new IllegalArgumentException("Das Bild darf maximal 200 KB groß sein.");
        }
        Unit unit = requireUnit(unitId);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        unit.setLogoBase64("data:" + contentType + ";base64," + base64);
        unitRepository.save(unit);
    }

    @Transactional
    public void clearLogo(long unitId) {
        Unit unit = requireUnit(unitId);
        unit.setLogoBase64(null);
        unitRepository.save(unit);
    }

    @Transactional(readOnly = true)
    public UnitSmtpSettings getOrCreateSmtp(long unitId) {
        return smtpSettingsRepository
                .findById(unitId)
                .orElseGet(() -> {
                    UnitSmtpSettings s = new UnitSmtpSettings();
                    s.setUnit(requireUnit(unitId));
                    return smtpSettingsRepository.save(s);
                });
    }

    @Transactional
    public UnitSmtpSettings saveSmtp(
            long unitId,
            String host,
            Integer port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String encryption) {
        UnitSmtpSettings s = getOrCreateSmtp(unitId);
        s.setSmtpHost(trimToNull(host));
        s.setSmtpPort(port);
        s.setSmtpUsername(trimToNull(username));
        if (StringUtils.hasText(password)) {
            s.setSmtpPassword(password.trim());
        }
        s.setSmtpFromEmail(trimToNull(fromEmail));
        s.setSmtpFromName(trimToNull(fromName));
        s.setSmtpEncryption(encryption != null && !encryption.isBlank() ? encryption.trim() : "TLS");
        return smtpSettingsRepository.save(s);
    }

    public boolean isSmtpPasswordConfigured(long unitId) {
        return smtpSettingsRepository
                .findById(unitId)
                .map(s -> s.getSmtpPassword() != null && !s.getSmtpPassword().isBlank())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public UnitCalendarSettings getOrCreateCalendar(long unitId) {
        return calendarSettingsRepository
                .findById(unitId)
                .orElseGet(() -> {
                    UnitCalendarSettings c = new UnitCalendarSettings();
                    c.setUnit(requireUnit(unitId));
                    return calendarSettingsRepository.save(c);
                });
    }

    @Transactional
    public UnitCalendarSettings saveCalendar(long unitId, String calendarUrl, String calendarId, boolean enabled) {
        UnitCalendarSettings c = getOrCreateCalendar(unitId);
        c.setCalendarUrl(trimToNull(calendarUrl));
        c.setCalendarId(trimToNull(calendarId));
        c.setEnabled(enabled);
        c.setProvider("google");
        return calendarSettingsRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listVehicles(long unitId) {
        return vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.testDataScope());
    }

    @Transactional
    public Vehicle createVehicle(long unitId, String name, String description) {
        Unit unit = requireUnit(unitId);
        Vehicle v = new Vehicle();
        v.setUnit(unit);
        v.setName(requireName(name));
        v.setDescription(trimToNull(description));
        v.setTestData(testModeService.testDataScope());
        return vehicleRepository.save(v);
    }

    @Transactional
    public Vehicle updateVehicle(long unitId, long vehicleId, String name, String description, boolean active) {
        Vehicle v = vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        v.setName(requireName(name));
        v.setDescription(trimToNull(description));
        v.setActive(active);
        return vehicleRepository.save(v);
    }

    @Transactional
    public void deleteVehicle(long unitId, long vehicleId) {
        Vehicle v = vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        vehicleRepository.delete(v);
    }

    @Transactional(readOnly = true)
    public List<Room> listRooms(long unitId) {
        return roomRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.testDataScope());
    }

    @Transactional
    public Room createRoom(long unitId, String name, String description) {
        Unit unit = requireUnit(unitId);
        Room r = new Room();
        r.setUnit(unit);
        r.setName(requireName(name));
        r.setDescription(trimToNull(description));
        r.setTestData(testModeService.testDataScope());
        return roomRepository.save(r);
    }

    @Transactional
    public Room updateRoom(long unitId, long roomId, String name, String description, boolean active) {
        Room r = roomRepository
                .findByIdAndUnitId(roomId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        r.setName(requireName(name));
        r.setDescription(trimToNull(description));
        r.setActive(active);
        return roomRepository.save(r);
    }

    @Transactional
    public void deleteRoom(long unitId, long roomId) {
        Room r = roomRepository
                .findByIdAndUnitId(roomId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        roomRepository.delete(r);
    }

    @Transactional(readOnly = true)
    public List<VehicleEquipmentCategory> listEquipmentCategories(long vehicleId) {
        return equipmentCategoryRepository.findByVehicleIdOrderBySortOrderAscNameAsc(vehicleId);
    }

    @Transactional(readOnly = true)
    public List<VehicleEquipment> listEquipment(long vehicleId) {
        return equipmentRepository.findByVehicleIdOrderBySortOrderAscNameAsc(vehicleId);
    }

    @Transactional
    public VehicleEquipment createEquipment(long unitId, long vehicleId, String name, Long categoryId) {
        Vehicle vehicle = vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        VehicleEquipment eq = new VehicleEquipment();
        eq.setVehicle(vehicle);
        eq.setName(requireName(name));
        if (categoryId != null && categoryId > 0) {
            equipmentCategoryRepository.findById(categoryId).ifPresent(eq::setCategory);
        }
        return equipmentRepository.save(eq);
    }

    @Transactional
    public void deleteEquipment(long unitId, long equipmentId) {
        VehicleEquipment eq = equipmentRepository
                .findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Gerät nicht gefunden."));
        if (!eq.getVehicle().getUnit().getId().equals(unitId)) {
            throw new IllegalArgumentException("Gerät gehört nicht zu dieser Einheit.");
        }
        equipmentRepository.delete(eq);
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen eingeben.");
        }
        return name.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
