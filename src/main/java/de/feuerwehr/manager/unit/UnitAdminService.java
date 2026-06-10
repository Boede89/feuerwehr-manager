package de.feuerwehr.manager.unit;

import de.feuerwehr.manager.settings.TestModeDataMerge;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.technik.Room;
import de.feuerwehr.manager.technik.RoomRepository;
import de.feuerwehr.manager.technik.Vehicle;
import de.feuerwehr.manager.technik.VehicleFormData;
import de.feuerwehr.manager.technik.VehicleServiceStatus;
import de.feuerwehr.manager.technik.UnitVehicleTypeService;
import de.feuerwehr.manager.technik.UnitEquipmentCategory;
import de.feuerwehr.manager.technik.UnitEquipmentCategoryRepository;
import de.feuerwehr.manager.technik.VehicleEquipment;
import de.feuerwehr.manager.technik.VehicleEquipmentRepository;
import de.feuerwehr.manager.technik.VehicleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final UnitEquipmentCategoryRepository equipmentCategoryRepository;
    private final VehicleEquipmentRepository equipmentRepository;
    private final TestModeService testModeService;
    private final UnitVehicleTypeService unitVehicleTypeService;

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
        if (!testModeService.isEnabled()) {
            return vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        }
        List<Vehicle> production =
                vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        List<Vehicle> testRows =
                vehicleRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, true);
        return TestModeDataMerge.mergeByProductionSource(
                production,
                testRows,
                Vehicle::getProductionSourceId,
                Vehicle::getId,
                Comparator.comparing(Vehicle::getSortOrder).thenComparing(Vehicle::getName));
    }

    @Transactional
    public Vehicle createVehicle(long unitId, VehicleFormData form) {
        Unit unit = requireUnit(unitId);
        Vehicle v = new Vehicle();
        v.setUnit(unit);
        v.setTestData(testModeService.isEnabled());
        v.setSortOrder(listVehicles(unitId).size());
        applyVehicleForm(v, form);
        return vehicleRepository.save(v);
    }

    @Transactional
    public Vehicle updateVehicle(long unitId, long vehicleId, VehicleFormData form) {
        Vehicle v = writableVehicle(resolveVehicle(unitId, vehicleId));
        applyVehicleForm(v, form);
        return vehicleRepository.save(v);
    }

    private void applyVehicleForm(Vehicle v, VehicleFormData form) {
        v.setName(requireName(form.name()));
        v.setDescription(trimToNull(form.description()));
        v.setVehicleType(unitVehicleTypeService.normalizeKey(v.getUnit().getId(), form.vehicleType()));
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
    public void moveVehicle(long unitId, long vehicleId, String direction) {
        List<Vehicle> items = new ArrayList<>(listVehicles(unitId));
        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(vehicleId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new IllegalArgumentException("Fahrzeug nicht gefunden.");
        }
        int delta = "down".equalsIgnoreCase(direction != null ? direction.trim() : "") ? 1 : -1;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= items.size()) {
            return;
        }
        java.util.Collections.swap(items, idx, newIdx);
        for (int i = 0; i < items.size(); i++) {
            Vehicle v = writableVehicle(resolveVehicle(unitId, items.get(i).getId()));
            v.setSortOrder(i);
            vehicleRepository.save(v);
        }
    }

    @Transactional
    public void deleteVehicle(long unitId, long vehicleId) {
        Vehicle v = resolveVehicle(unitId, vehicleId);
        if (testModeService.isEnabled() && !v.isTestData()) {
            throw new IllegalArgumentException("Produktiv-Fahrzeuge können im Testmodus nicht gelöscht werden.");
        }
        vehicleRepository.delete(v);
    }

    @Transactional(readOnly = true)
    public List<Room> listRooms(long unitId) {
        if (!testModeService.isEnabled()) {
            return roomRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        }
        List<Room> production = roomRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, false);
        List<Room> testRows = roomRepository.findByUnitIdAndTestDataOrderBySortOrderAscNameAsc(unitId, true);
        return TestModeDataMerge.mergeByProductionSource(
                production,
                testRows,
                Room::getProductionSourceId,
                Room::getId,
                Comparator.comparing(Room::getSortOrder).thenComparing(Room::getName));
    }

    @Transactional
    public Room createRoom(long unitId, String name, String description) {
        Unit unit = requireUnit(unitId);
        Room r = new Room();
        r.setUnit(unit);
        r.setName(requireName(name));
        r.setDescription(trimToNull(description));
        r.setTestData(testModeService.isEnabled());
        return roomRepository.save(r);
    }

    @Transactional
    public Room updateRoom(long unitId, long roomId, String name, String description, boolean active) {
        Room r = writableRoom(resolveRoom(unitId, roomId));
        r.setName(requireName(name));
        r.setDescription(trimToNull(description));
        r.setActive(active);
        return roomRepository.save(r);
    }

    @Transactional
    public void deleteRoom(long unitId, long roomId) {
        Room r = resolveRoom(unitId, roomId);
        if (testModeService.isEnabled() && !r.isTestData()) {
            throw new IllegalArgumentException("Produktiv-Räume können im Testmodus nicht gelöscht werden.");
        }
        roomRepository.delete(r);
    }

    @Transactional(readOnly = true)
    public List<UnitEquipmentCategory> listEquipmentCategories(long unitId) {
        return equipmentCategoryRepository.findByUnitIdOrderBySortOrderAscNameAsc(unitId);
    }

    @Transactional(readOnly = true)
    public List<VehicleEquipment> listEquipment(long vehicleId) {
        return equipmentRepository.findByVehicleIdWithCategoryOrderBySortOrderAscNameAsc(vehicleId);
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
    public UnitEquipmentCategory createEquipmentCategory(long unitId, String name) {
        Unit unit = requireUnit(unitId);
        String categoryName = requireName(name);
        if (equipmentCategoryRepository.existsByUnitIdAndNameIgnoreCase(unitId, categoryName)) {
            throw new IllegalArgumentException("Diese Kategorie existiert bereits.");
        }
        UnitEquipmentCategory category = new UnitEquipmentCategory();
        category.setUnit(unit);
        category.setName(categoryName);
        category.setSortOrder(equipmentCategoryRepository.findByUnitIdOrderBySortOrderAscNameAsc(unitId).size());
        return equipmentCategoryRepository.save(category);
    }

    @Transactional
    public void deleteEquipmentCategory(long unitId, long categoryId) {
        UnitEquipmentCategory category = equipmentCategoryRepository
                .findByIdAndUnitId(categoryId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Kategorie nicht gefunden."));
        equipmentCategoryRepository.delete(category);
    }

    @Transactional
    public VehicleEquipment createEquipment(long unitId, long vehicleId, String name, Long categoryId) {
        Vehicle vehicle = writableVehicle(resolveVehicle(unitId, vehicleId));
        String equipmentName = requireName(name);
        long resolvedVehicleId = vehicle.getId();
        if (equipmentRepository.existsByVehicleIdAndNameIgnoreCase(resolvedVehicleId, equipmentName)) {
            throw new IllegalArgumentException("Dieses Gerät ist bereits hinterlegt.");
        }
        VehicleEquipment eq = new VehicleEquipment();
        eq.setVehicle(vehicle);
        eq.setName(equipmentName);
        if (categoryId != null && categoryId > 0) {
            equipmentCategoryRepository
                    .findByIdAndUnitId(categoryId, unitId)
                    .ifPresent(eq::setCategory);
        }
        eq.setSortOrder((int) equipmentRepository.countByVehicleId(resolvedVehicleId));
        return equipmentRepository.save(eq);
    }

    @Transactional
    public VehicleEquipment updateEquipment(
            long unitId, long vehicleId, long equipmentId, String name, Long categoryId) {
        Vehicle vehicle = writableVehicle(resolveVehicle(unitId, vehicleId));
        long resolvedVehicleId = vehicle.getId();
        VehicleEquipment eq = equipmentRepository
                .findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Gerät nicht gefunden."));
        if (!eq.getVehicle().getId().equals(resolvedVehicleId)) {
            throw new IllegalArgumentException("Gerät gehört nicht zu diesem Fahrzeug.");
        }
        eq.setName(requireName(name));
        if (categoryId != null && categoryId > 0) {
            equipmentCategoryRepository
                    .findByIdAndUnitId(categoryId, unitId)
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

    private Vehicle resolveVehicle(long unitId, long vehicleId) {
        if (!testModeService.isEnabled()) {
            return vehicleRepository
                    .findByIdAndUnitId(vehicleId, unitId)
                    .filter(v -> !v.isTestData())
                    .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        }
        Optional<Vehicle> testRow = vehicleRepository.findByIdAndUnitId(vehicleId, unitId).filter(Vehicle::isTestData);
        if (testRow.isPresent()) {
            return testRow.get();
        }
        Vehicle prod = vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .filter(v -> !v.isTestData())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
        return vehicleRepository.findShadowByProductionSourceId(prod.getId()).orElse(prod);
    }

    private Vehicle writableVehicle(Vehicle viewed) {
        if (!testModeService.isEnabled() || viewed.isTestData()) {
            return viewed;
        }
        return vehicleRepository
                .findShadowByProductionSourceId(viewed.getId())
                .orElseGet(() -> {
                    Vehicle shadow = vehicleRepository.save(copyVehicleToShadow(viewed));
                    copyEquipmentToShadowVehicle(viewed, shadow);
                    return shadow;
                });
    }

    private Vehicle copyVehicleToShadow(Vehicle prod) {
        Vehicle shadow = new Vehicle();
        shadow.setUnit(prod.getUnit());
        shadow.setName(prod.getName());
        shadow.setDescription(prod.getDescription());
        shadow.setVehicleType(prod.getVehicleType());
        shadow.setLicensePlate(prod.getLicensePlate());
        shadow.setYearBuilt(prod.getYearBuilt());
        shadow.setPhone(prod.getPhone());
        shadow.setLengthM(prod.getLengthM());
        shadow.setWidthM(prod.getWidthM());
        shadow.setHeightM(prod.getHeightM());
        shadow.setWeightKg(prod.getWeightKg());
        shadow.setServiceStatus(prod.getServiceStatus());
        shadow.setNotes(prod.getNotes());
        shadow.setActive(prod.isActive());
        shadow.setSortOrder(prod.getSortOrder());
        shadow.setTestData(true);
        shadow.setProductionSourceId(prod.getId());
        return shadow;
    }

    private void copyEquipmentToShadowVehicle(Vehicle prod, Vehicle shadow) {
        for (VehicleEquipment item :
                equipmentRepository.findByVehicleIdWithCategoryOrderBySortOrderAscNameAsc(prod.getId())) {
            VehicleEquipment copy = new VehicleEquipment();
            copy.setVehicle(shadow);
            copy.setCategory(item.getCategory());
            copy.setName(item.getName());
            copy.setSortOrder(item.getSortOrder());
            equipmentRepository.save(copy);
        }
    }

    private Room resolveRoom(long unitId, long roomId) {
        if (!testModeService.isEnabled()) {
            return roomRepository
                    .findByIdAndUnitId(roomId, unitId)
                    .filter(r -> !r.isTestData())
                    .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        }
        Optional<Room> testRow = roomRepository.findByIdAndUnitId(roomId, unitId).filter(Room::isTestData);
        if (testRow.isPresent()) {
            return testRow.get();
        }
        Room prod = roomRepository
                .findByIdAndUnitId(roomId, unitId)
                .filter(r -> !r.isTestData())
                .orElseThrow(() -> new IllegalArgumentException("Raum nicht gefunden."));
        return roomRepository.findShadowByProductionSourceId(prod.getId()).orElse(prod);
    }

    private Room writableRoom(Room viewed) {
        if (!testModeService.isEnabled() || viewed.isTestData()) {
            return viewed;
        }
        return roomRepository
                .findShadowByProductionSourceId(viewed.getId())
                .orElseGet(() -> roomRepository.save(copyRoomToShadow(viewed)));
    }

    private Room copyRoomToShadow(Room prod) {
        Room shadow = new Room();
        shadow.setUnit(prod.getUnit());
        shadow.setName(prod.getName());
        shadow.setDescription(prod.getDescription());
        shadow.setActive(prod.isActive());
        shadow.setSortOrder(prod.getSortOrder());
        shadow.setTestData(true);
        shadow.setProductionSourceId(prod.getId());
        return shadow;
    }

    private Unit requireUnit(long unitId) {
        return unitRepository
                .findVisibleById(unitId, testModeService.isEnabled())
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
