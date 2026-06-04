package de.feuerwehr.manager.technik;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class VehicleChecklistService {

    private final VehicleRepository vehicleRepository;
    private final VehicleChecklistTemplateRepository templateRepository;
    private final VehicleChecklistItemRepository itemRepository;
    private final VehicleChecklistRepository checklistRepository;
    private final VehicleChecklistEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChecklistTemplateRow> listTemplates(long unitId, long vehicleId) {
        requireVehicle(unitId, vehicleId);
        return templateRepository.findByVehicleIdOrderByNameAsc(vehicleId).stream()
                .map(this::toTemplateRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChecklistHistoryRow> listHistory(long unitId, long vehicleId) {
        requireVehicle(unitId, vehicleId);
        List<ChecklistHistoryRow> rows = new ArrayList<>();
        for (VehicleChecklist c : checklistRepository.findByVehicleIdWithTemplateOrderByFilledAtDesc(vehicleId)) {
            var counts = countResults(c.getId());
            rows.add(new ChecklistHistoryRow(
                    c.getId(),
                    c.getTemplate().getName(),
                    c.getFilledAt(),
                    displayFilledName(c),
                    counts[0],
                    counts[1]));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Optional<ChecklistDetailRow> getDetail(long unitId, long vehicleId, long checklistId) {
        requireVehicle(unitId, vehicleId);
        return checklistRepository
                .findByIdAndVehicleIdWithDetails(checklistId, vehicleId)
                .map(c -> {
                    var entries = entryRepository.findByChecklistIdOrderByIdAsc(c.getId()).stream()
                            .map(e -> new ChecklistDetailEntryRow(e.getItemLabel(), e.getResult(), e.getNote()))
                            .toList();
                    return new ChecklistDetailRow(
                            c.getId(),
                            c.getTemplate().getName(),
                            c.getFilledAt(),
                            displayFilledName(c),
                            c.getNotes(),
                            entries);
                });
    }

    @Transactional
    public void createTemplate(
            long unitId, long vehicleId, String name, String interval, List<String> itemLabels, Long createdByUserId) {
        Vehicle vehicle = requireVehicle(unitId, vehicleId);
        String templateName = requireName(name);
        VehicleChecklistTemplate template = new VehicleChecklistTemplate();
        template.setVehicle(vehicle);
        template.setName(templateName);
        template.setIntervalType(ChecklistInterval.fromKey(interval).key());
        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(template::setCreatedBy);
        }
        template = templateRepository.save(template);

        List<String> labels = normalizeItemLabels(itemLabels);
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("Mindestens einen Prüfpunkt eingeben.");
        }
        int pos = 0;
        for (String label : labels) {
            VehicleChecklistItem item = new VehicleChecklistItem();
            item.setTemplate(template);
            item.setPosition(pos++);
            item.setLabel(label);
            itemRepository.save(item);
        }
    }

    @Transactional
    public void deleteTemplate(long unitId, long vehicleId, long templateId) {
        requireVehicle(unitId, vehicleId);
        VehicleChecklistTemplate template = templateRepository
                .findByIdAndVehicleId(templateId, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vorlage nicht gefunden."));
        templateRepository.delete(template);
    }

    @Transactional
    public void createChecklist(
            long unitId,
            long vehicleId,
            long templateId,
            String notes,
            List<Long> itemIds,
            List<String> results,
            List<String> notesPerItem,
            Long filledByUserId,
            String filledByDisplayName) {
        Vehicle vehicle = requireVehicle(unitId, vehicleId);
        VehicleChecklistTemplate template = templateRepository
                .findByIdAndVehicleId(templateId, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vorlage nicht gefunden."));

        List<VehicleChecklistItem> templateItems = itemRepository.findByTemplateIdOrderByPositionAscIdAsc(templateId);
        if (templateItems.isEmpty()) {
            throw new IllegalArgumentException("Vorlage hat keine Prüfpunkte.");
        }

        VehicleChecklist checklist = new VehicleChecklist();
        checklist.setVehicle(vehicle);
        checklist.setTemplate(template);
        checklist.setNotes(trimToNull(notes));
        checklist.setFilledName(resolveFilledName(filledByUserId, filledByDisplayName));
        if (filledByUserId != null) {
            userRepository.findById(filledByUserId).ifPresent(checklist::setFilledBy);
        }
        checklist = checklistRepository.save(checklist);

        for (int i = 0; i < templateItems.size(); i++) {
            VehicleChecklistItem item = templateItems.get(i);
            Long submittedId = itemIds != null && i < itemIds.size() ? itemIds.get(i) : item.getId();
            if (!item.getId().equals(submittedId)) {
                throw new IllegalArgumentException("Ungültige Prüfpunkte.");
            }
            String resultKey =
                    results != null && i < results.size() ? ChecklistResult.fromKey(results.get(i)).key() : "ok";
            String note = notesPerItem != null && i < notesPerItem.size() ? trimToNull(notesPerItem.get(i)) : null;

            VehicleChecklistEntry entry = new VehicleChecklistEntry();
            entry.setChecklist(checklist);
            entry.setItem(item);
            entry.setItemLabel(item.getLabel());
            entry.setResult(resultKey);
            entry.setNote(note);
            entryRepository.save(entry);
        }
    }

    @Transactional
    public void deleteChecklist(long unitId, long vehicleId, long checklistId) {
        requireVehicle(unitId, vehicleId);
        VehicleChecklist checklist = checklistRepository
                .findByIdAndVehicleIdWithDetails(checklistId, vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Checkliste nicht gefunden."));
        entryRepository.deleteByChecklistId(checklist.getId());
        checklistRepository.delete(checklist);
    }

    private ChecklistTemplateRow toTemplateRow(VehicleChecklistTemplate template) {
        List<VehicleChecklistItem> items = itemRepository.findByTemplateIdOrderByPositionAscIdAsc(template.getId());
        List<ChecklistTemplateItemRow> itemRows =
                items.stream().map(i -> new ChecklistTemplateItemRow(i.getId(), i.getLabel())).toList();
        return new ChecklistTemplateRow(
                template.getId(),
                template.getName(),
                template.getIntervalType(),
                ChecklistInterval.labelFor(template.getIntervalType()),
                items.size(),
                itemRows,
                toItemsJson(itemRows));
    }

    private String toItemsJson(List<ChecklistTemplateItemRow> itemRows) {
        try {
            return objectMapper.writeValueAsString(itemRows);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private int[] countResults(long checklistId) {
        int ok = 0;
        int mangel = 0;
        for (VehicleChecklistEntry e : entryRepository.findByChecklistIdOrderByIdAsc(checklistId)) {
            if ("ok".equals(e.getResult())) {
                ok++;
            } else if ("mangel".equals(e.getResult())) {
                mangel++;
            }
        }
        return new int[] {ok, mangel};
    }

    private static List<String> normalizeItemLabels(List<String> itemLabels) {
        if (itemLabels == null) {
            return List.of();
        }
        return itemLabels.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private static String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Name eingeben.");
        }
        return name.trim();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveFilledName(Long userId, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        if (userId != null) {
            return userRepository.findById(userId).map(User::getDisplayName).orElse("Unbekannt");
        }
        return "Unbekannt";
    }

    private static String displayFilledName(VehicleChecklist c) {
        if (StringUtils.hasText(c.getFilledName())) {
            return c.getFilledName();
        }
        if (c.getFilledBy() != null && StringUtils.hasText(c.getFilledBy().getDisplayName())) {
            return c.getFilledBy().getDisplayName();
        }
        return "Unbekannt";
    }

    private Vehicle requireVehicle(long unitId, long vehicleId) {
        return vehicleRepository
                .findByIdAndUnitId(vehicleId, unitId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden."));
    }
}
