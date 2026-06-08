package de.feuerwehr.manager.divera;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiveraMappingService {

    private final UnitDiveraRecipientGroupRepository recipientGroupRepository;
    private final UnitDiveraStatusIdRepository statusIdRepository;
    private final UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public List<UnitDiveraRecipientGroup> listRecipientGroups(long unitId) {
        return recipientGroupRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
    }

    @Transactional(readOnly = true)
    public List<UnitDiveraStatusId> listStatusIds(long unitId) {
        return statusIdRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId);
    }

    @Transactional
    public UnitDiveraRecipientGroup createRecipientGroup(long unitId, String groupId, String label) {
        String normalizedId = normalizeOptionalGroupId(groupId);
        String normalizedLabel = normalizeLabel(label);
        if (normalizedId != null) {
            if (recipientGroupRepository.existsByUnitIdAndGroupId(unitId, normalizedId)) {
                throw new IllegalArgumentException("Diese Empfänger-Gruppen-ID ist bereits hinterlegt.");
            }
        } else if (recipientGroupRepository.existsByUnitIdAndGroupIdIsNullAndLabel(unitId, normalizedLabel)) {
            throw new IllegalArgumentException("Diese Bezeichnung ist bereits ohne ID hinterlegt.");
        }
        Unit unit = requireUnit(unitId);
        int nextOrder = recipientGroupRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId).size() + 1;
        UnitDiveraRecipientGroup row = new UnitDiveraRecipientGroup();
        row.setUnit(unit);
        row.setGroupId(normalizedId);
        row.setLabel(normalizedLabel);
        row.setSortOrder(nextOrder);
        return recipientGroupRepository.save(row);
    }

    @Transactional
    public void deleteRecipientGroup(long unitId, long rowId) {
        UnitDiveraRecipientGroup row = recipientGroupRepository
                .findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Empfänger-Gruppe nicht gefunden."));
        if (row.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Empfänger-Gruppe gehört nicht zu dieser Einheit.");
        }
        recipientGroupRepository.delete(row);
    }

    @Transactional
    public UnitDiveraStatusId createStatusId(long unitId, String statusId, String label) {
        String normalizedId = normalizeId(statusId, "Status-ID");
        String normalizedLabel = normalizeLabel(label);
        if (statusIdRepository.existsByUnitIdAndStatusId(unitId, normalizedId)) {
            throw new IllegalArgumentException("Diese Status-ID ist bereits hinterlegt.");
        }
        Unit unit = requireUnit(unitId);
        int nextOrder = statusIdRepository.findByUnitIdOrderBySortOrderAscLabelAsc(unitId).size() + 1;
        UnitDiveraStatusId row = new UnitDiveraStatusId();
        row.setUnit(unit);
        row.setStatusId(normalizedId);
        row.setLabel(normalizedLabel);
        row.setSortOrder(nextOrder);
        return statusIdRepository.save(row);
    }

    @Transactional
    public void deleteStatusId(long unitId, long rowId) {
        UnitDiveraStatusId row = statusIdRepository
                .findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Status-ID nicht gefunden."));
        if (row.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Status-ID gehört nicht zu dieser Einheit.");
        }
        statusIdRepository.delete(row);
    }

    private static String normalizeOptionalGroupId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ist Pflichtfeld.");
        }
        return value.trim();
    }

    private static String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bezeichnung ist Pflichtfeld.");
        }
        return value.trim();
    }

    private Unit requireUnit(long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }
}
