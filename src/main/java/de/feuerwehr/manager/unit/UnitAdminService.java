package de.feuerwehr.manager.unit;

import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Room;
import de.feuerwehr.manager.technik.RoomRepository;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleFormData;
import de.feuerwehr.manager.technik.VehicleServiceStatus;
import de.feuerwehr.manager.technik.VehicleTypes;
import de.feuerwehr.manager.technik.VehicleEquipment;
import de.feuerwehr.manager.technik.VehicleEquipmentCategory;
import de.feuerwehr.manager.technik.VehicleEquipmentCategoryRepository;
import de.feuerwehr.manager.technik.VehicleEquipmentRepository;
import de.feuerwehr.manager.technik.VehicleRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UnitAdminService {

    private final UnitRepository unitRepository;
    private final UnitSmtpAccountRepository smtpAccountRepository;
    private final UnitCalendarAccountRepository calendarAccountRepository;
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
    public List<UnitSmtpAccount> listSmtpAccounts(long unitId) {
        return smtpAccountRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
    }

    @Transactional(readOnly = true)
    public UnitSmtpAccount requireSmtpAccount(long unitId, long accountId) {
        return smtpAccountRepository
                .findByIdAndUnitId(accountId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("SMTP-Konto nicht gefunden."));
    }

    @Transactional
    public UnitSmtpAccount createSmtpAccount(
            long unitId,
            String label,
            String host,
            Integer port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String encryption) {
        Unit unit = requireUnit(unitId);
        UnitSmtpAccount a = new UnitSmtpAccount();
        a.setUnit(unit);
        a.setLabel(requireLabel(label));
        applySmtpFields(a, host, port, username, password, fromEmail, fromName, encryption, true);
        a.setSortOrder(listSmtpAccounts(unitId).size());
        return smtpAccountRepository.save(a);
    }

    @Transactional
    public UnitSmtpAccount updateSmtpAccount(
            long unitId,
            long accountId,
            String label,
            String host,
            Integer port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String encryption) {
        UnitSmtpAccount a = requireSmtpAccount(unitId, accountId);
        a.setLabel(requireLabel(label));
        applySmtpFields(a, host, port, username, password, fromEmail, fromName, encryption, false);
        a.setUpdatedAt(Instant.now());
        return smtpAccountRepository.save(a);
    }

    @Transactional
    public void deleteSmtpAccount(long unitId, long accountId) {
        smtpAccountRepository.delete(requireSmtpAccount(unitId, accountId));
    }

    public boolean isSmtpPasswordConfigured(UnitSmtpAccount account) {
        return account != null
                && account.getSmtpPassword() != null
                && !account.getSmtpPassword().isBlank();
    }

    @Transactional(readOnly = true)
    public List<UnitCalendarAccount> listCalendarAccounts(long unitId) {
        return calendarAccountRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
    }

    @Transactional(readOnly = true)
    public UnitCalendarAccount requireCalendarAccount(long unitId, long accountId) {
        return calendarAccountRepository
                .findByIdAndUnitId(accountId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kalender nicht gefunden."));
    }

    @Transactional
    public UnitCalendarAccount createCalendarAccount(
            long unitId,
            String label,
            String calendarUrl,
            String calendarId,
            String serviceAccountJson,
            boolean enabled) {
        Unit unit = requireUnit(unitId);
        UnitCalendarAccount c = new UnitCalendarAccount();
        c.setUnit(unit);
        c.setLabel(requireLabel(label));
        applyCalendarFields(c, calendarUrl, calendarId, serviceAccountJson, enabled, true);
        c.setSortOrder(listCalendarAccounts(unitId).size());
        return calendarAccountRepository.save(c);
    }

    @Transactional
    public UnitCalendarAccount updateCalendarAccount(
            long unitId,
            long accountId,
            String label,
            String calendarUrl,
            String calendarId,
            String serviceAccountJson,
            boolean enabled) {
        UnitCalendarAccount c = requireCalendarAccount(unitId, accountId);
        c.setLabel(requireLabel(label));
        applyCalendarFields(c, calendarUrl, calendarId, serviceAccountJson, enabled, false);
        c.setUpdatedAt(java.time.Instant.now());
        return calendarAccountRepository.save(c);
    }

    @Transactional
    public void deleteCalendarAccount(long unitId, long accountId) {
        calendarAccountRepository.delete(requireCalendarAccount(unitId, accountId));
    }

    public boolean isCalendarCredentialsConfigured(UnitCalendarAccount account) {
        return account != null
                && account.getServiceAccountJson() != null
                && !account.getServiceAccountJson().isBlank();
    }

    private static void applySmtpFields(
            UnitSmtpAccount a,
            String host,
            Integer port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String encryption,
            boolean isCreate) {
        a.setSmtpHost(trimToNull(host));
        a.setSmtpPort(port);
        a.setSmtpUsername(trimToNull(username));
        if (StringUtils.hasText(password)) {
            a.setSmtpPassword(password.trim());
        } else if (isCreate) {
            a.setSmtpPassword(null);
        }
        a.setSmtpFromEmail(trimToNull(fromEmail));
        a.setSmtpFromName(trimToNull(fromName));
        a.setSmtpEncryption(encryption != null && !encryption.isBlank() ? encryption.trim() : "TLS");
    }

    private static void applyCalendarFields(
            UnitCalendarAccount c,
            String calendarUrl,
            String calendarId,
            String serviceAccountJson,
            boolean enabled,
            boolean isCreate) {
        c.setCalendarUrl(trimToNull(calendarUrl));
        c.setCalendarId(trimToNull(calendarId));
        if (serviceAccountJson != null) {
            String json = serviceAccountJson.trim();
            if (!json.isEmpty()) {
                c.setServiceAccountJson(json);
            }
        } else if (isCreate) {
            c.setServiceAccountJson(null);
        }
        c.setEnabled(enabled);
        c.setProvider("google");
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Bezeichnung eingeben.");
        }
        return label.trim();
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listVehicles(long unitId) {
        return vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, testModeService.testDataScope());
    }

    @Transactional
    public Vehicle createVehicle(long unitId, VehicleFormData form) {
        Unit unit = requireUnit(unitId);
        Vehicle v = new Vehicle();
        v.setUnit(unit);
        v.setTestData(testModeService.testDataScope());
        v.setSortOrder(listVehicles(unitId).size());
        applyVehicleForm(v, form);
        return vehicleRepository.save(v);
    }

    @Transactional
    public Vehicle updateVehicle(long unitId, long vehicleId, VehicleFormData form) {
        Vehicle v = vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        applyVehicleForm(v, form);
        return vehicleRepository.save(v);
    }

    private static void applyVehicleForm(Vehicle v, VehicleFormData form) {
        v.setName(requireName(form.name()));
        v.setDescription(trimToNull(form.description()));
        v.setVehicleType(VehicleTypes.normalizeKey(form.vehicleType()));
        v.setLicensePlate(trimToNull(form.licensePlate()));
        v.setYearBuilt(form.yearBuilt());
        v.setPhone(trimToNull(form.phone()));
        v.setLengthM(form.lengthM());
        v.setWidthM(form.widthM());
        v.setHeightM(form.heightM());
        v.setWeightKg(form.weightKg());
        String status = VehicleServiceStatus.normalize(form.serviceStatus());
        v.setServiceStatus(status);
        v.setActive(VehicleServiceStatus.isActive(status));
        v.setNotes(trimToNull(form.notes()));
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

    @Transactional(readOnly = true)
    public Map<Long, Long> equipmentCountByVehicleId(long unitId) {
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Vehicle vehicle : listVehicles(unitId)) {
            counts.put(vehicle.getId(), equipmentRepository.countByVehicleId(vehicle.getId()));
        }
        return counts;
    }

    @Transactional
    public VehicleEquipmentCategory createEquipmentCategory(long unitId, long vehicleId, String name) {
        Vehicle vehicle = requireVehicle(unitId, vehicleId);
        String categoryName = requireName(name);
        if (equipmentCategoryRepository.existsByVehicleIdAndNameIgnoreCase(vehicleId, categoryName)) {
            throw new IllegalArgumentException("Diese Kategorie existiert bereits.");
        }
        VehicleEquipmentCategory category = new VehicleEquipmentCategory();
        category.setVehicle(vehicle);
        category.setName(categoryName);
        category.setSortOrder(equipmentCategoryRepository.findByVehicleIdOrderBySortOrderAscNameAsc(vehicleId).size());
        return equipmentCategoryRepository.save(category);
    }

    @Transactional
    public void deleteEquipmentCategory(long unitId, long vehicleId, long categoryId) {
        requireVehicle(unitId, vehicleId);
        VehicleEquipmentCategory category = equipmentCategoryRepository
                .findByIdAndVehicleId(categoryId, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Kategorie nicht gefunden."));
        equipmentCategoryRepository.delete(category);
    }

    @Transactional
    public VehicleEquipment createEquipment(long unitId, long vehicleId, String name, Long categoryId) {
        Vehicle vehicle = requireVehicle(unitId, vehicleId);
        String equipmentName = requireName(name);
        if (equipmentRepository.existsByVehicleIdAndNameIgnoreCase(vehicleId, equipmentName)) {
            throw new IllegalArgumentException("Dieses Gerät ist bereits hinterlegt.");
        }
        VehicleEquipment eq = new VehicleEquipment();
        eq.setVehicle(vehicle);
        eq.setName(equipmentName);
        if (categoryId != null && categoryId > 0) {
            equipmentCategoryRepository
                    .findByIdAndVehicleId(categoryId, vehicleId)
                    .ifPresent(eq::setCategory);
        }
        eq.setSortOrder((int) equipmentRepository.countByVehicleId(vehicleId));
        return equipmentRepository.save(eq);
    }

    @Transactional
    public VehicleEquipment updateEquipment(
            long unitId, long vehicleId, long equipmentId, String name, Long categoryId) {
        requireVehicle(unitId, vehicleId);
        VehicleEquipment eq = equipmentRepository
                .findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Gerät nicht gefunden."));
        if (!eq.getVehicle().getId().equals(vehicleId)) {
            throw new IllegalArgumentException("Gerät gehört nicht zu diesem Fahrzeug.");
        }
        eq.setName(requireName(name));
        if (categoryId != null && categoryId > 0) {
            equipmentCategoryRepository
                    .findByIdAndVehicleId(categoryId, vehicleId)
                    .ifPresentOrElse(eq::setCategory, () -> eq.setCategory(null));
        } else {
            eq.setCategory(null);
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

    private Vehicle requireVehicle(long unitId, long vehicleId) {
        return vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
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
