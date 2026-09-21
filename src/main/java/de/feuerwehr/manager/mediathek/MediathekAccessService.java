package de.feuerwehr.manager.mediathek;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.QualificationType;
import de.feuerwehr.manager.personal.QualificationTypeRepository;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.UnitRole;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediathekAccessService {

    private final MediathekFolderRepository folderRepository;
    private final PersonRepository personRepository;
    private final PersonGroupRepository personGroupRepository;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final UserRepository userRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public Optional<Person> linkedPerson(AppUserDetails actor, long unitId) {
        if (actor == null) {
            return Optional.empty();
        }
        return personRepository.findActiveByUserIdAndUnitId(
                actor.getUserId(), unitId, testModeService.isEnabled());
    }

    @Transactional(readOnly = true)
    public boolean isVisibleInUnit(MediathekFolder folder, long unitId) {
        return folder != null && folder.isSharedWith(unitId);
    }

    @Transactional(readOnly = true)
    public Optional<MediathekAccessLevel> effectiveLevel(
            AppUserDetails actor, long unitId, MediathekFolder folder) {
        if (actor == null || folder == null || !isVisibleInUnit(folder, unitId)) {
            return Optional.empty();
        }
        if (actor.getRole().isAdminLevel()) {
            return Optional.of(MediathekAccessLevel.WRITE);
        }
        MediathekFolder aclFolder = resolveAclFolder(folder);
        List<MediathekFolderAcl> entries =
                folderRepository.findByIdWithAcl(aclFolder.getId()).map(MediathekFolder::getAclEntries).orElse(List.of());
        // Ohne Feinrechte: für alle mit Modulzugriff lesbar (mediathek.read prüft der Controller)
        if (entries == null || entries.isEmpty()) {
            return Optional.of(MediathekAccessLevel.READ);
        }
        Optional<Person> personOpt = linkedPerson(actor, unitId);
        if (personOpt.isEmpty()) {
            return Optional.empty();
        }
        Person person = personOpt.get();
        long personId = person.getId();
        Set<Long> groupIds = new HashSet<>(personGroupRepository.findGroupIdsByMemberId(personId));
        UnitRole userDienstgrad = loadUserDienstgrad(actor);
        QualificationType effectiveQual = resolveEffectiveQualification(person, userDienstgrad, unitId);
        MediathekAccessLevel best = null;
        for (MediathekFolderAcl entry : entries) {
            if (!matchesEntry(entry, person, personId, groupIds, effectiveQual, userDienstgrad)) {
                continue;
            }
            if (best == null || entry.getAccessLevel() == MediathekAccessLevel.WRITE) {
                best = entry.getAccessLevel();
            }
            if (best == MediathekAccessLevel.WRITE) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Dienstgrad-ACL: Person muss denselben Einheits-Kontext haben und eine Qualifikation
     * mit gleicher oder höherer Stufe (niedrigere {@code sort_order}) besitzen.
     * Fallback: Dienstgrad-Rolle am Benutzerkonto (wenn an Qualifikation gekoppelt).
     */
    static boolean matchesQualification(QualificationType required, QualificationType personQual) {
        if (required == null || personQual == null || !personQual.isActive()) {
            return false;
        }
        Long requiredUnitId = required.getUnit() != null ? required.getUnit().getId() : null;
        Long personUnitId = personQual.getUnit() != null ? personQual.getUnit().getId() : null;
        if (requiredUnitId == null || !Objects.equals(requiredUnitId, personUnitId)) {
            return false;
        }
        return personQual.getSortOrder() <= required.getSortOrder();
    }

    /**
     * Fallback, wenn keine Personen-Qualifikation auflösbar ist: Vergleich über die
     * Dienstgrad-Rollen (niedrigere {@code sort_order} = höherer Dienstgrad).
     */
    static boolean matchesDienstgradRole(QualificationType required, UnitRole userDienstgrad) {
        if (required == null || userDienstgrad == null || required.getDienstgradRole() == null) {
            return false;
        }
        UnitRole requiredRole = required.getDienstgradRole();
        Long requiredUnitId = requiredRole.getUnit() != null
                ? requiredRole.getUnit().getId()
                : (required.getUnit() != null ? required.getUnit().getId() : null);
        Long userUnitId = userDienstgrad.getUnit() != null ? userDienstgrad.getUnit().getId() : null;
        if (requiredUnitId == null || !Objects.equals(requiredUnitId, userUnitId)) {
            return false;
        }
        return userDienstgrad.getSortOrder() <= requiredRole.getSortOrder();
    }

    private boolean matchesEntry(
            MediathekFolderAcl entry,
            Person person,
            long personId,
            Set<Long> groupIds,
            QualificationType effectiveQual,
            UnitRole userDienstgrad) {
        if (entry.getPerson() != null && entry.getPerson().getId().equals(personId)) {
            return true;
        }
        if (entry.getGroup() != null && groupIds.contains(entry.getGroup().getId())) {
            return true;
        }
        if (entry.getQualificationType() == null) {
            return false;
        }
        if (matchesQualification(entry.getQualificationType(), effectiveQual)) {
            return true;
        }
        return matchesDienstgradRole(entry.getQualificationType(), userDienstgrad);
    }

    /**
     * Qualifikation aus Personal, sonst Qualifikation die mit dem Benutzer-Dienstgrad verknüpft ist
     * (über Rolle oder Namensgleichheit in der Einheit).
     */
    QualificationType resolveEffectiveQualification(Person person, UnitRole userDienstgrad, long unitId) {
        if (person != null) {
            QualificationType fromPerson = person.getQualificationType();
            if (fromPerson != null && fromPerson.isActive()) {
                return fromPerson;
            }
        }
        if (userDienstgrad == null || userDienstgrad.getId() == null) {
            return null;
        }
        boolean testData = testModeService.isEnabled();
        List<QualificationType> linked = qualificationTypeRepository.findActiveByUnitIdAndDienstgradRoleId(
                unitId, userDienstgrad.getId(), testData);
        if (linked.isEmpty() && testData) {
            linked = qualificationTypeRepository.findActiveByUnitIdAndDienstgradRoleId(
                    unitId, userDienstgrad.getId(), false);
        }
        if (!linked.isEmpty()) {
            return linked.get(0);
        }
        String roleName = userDienstgrad.getName() != null ? userDienstgrad.getName().trim() : "";
        if (roleName.isEmpty()) {
            return null;
        }
        List<QualificationType> byUnit = qualificationTypeRepository
                .findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(unitId, testData);
        if (byUnit.isEmpty() && testData) {
            byUnit = qualificationTypeRepository.findByUnitIdAndTestDataAndActiveTrueOrderBySortOrderAscNameAsc(
                    unitId, false);
        }
        return byUnit.stream()
                .filter(q -> q.getName() != null && q.getName().trim().equalsIgnoreCase(roleName))
                .findFirst()
                .orElse(null);
    }

    private UnitRole loadUserDienstgrad(AppUserDetails actor) {
        if (actor == null) {
            return null;
        }
        return userRepository
                .findByIdWithUnit(actor.getUserId())
                .map(User::getOrganizationalRole)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean canRead(AppUserDetails actor, long unitId, MediathekFolder folder) {
        return effectiveLevel(actor, unitId, folder)
                .map(level -> level.includes(MediathekAccessLevel.READ))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canWrite(AppUserDetails actor, long unitId, MediathekFolder folder) {
        return effectiveLevel(actor, unitId, folder)
                .map(level -> level.includes(MediathekAccessLevel.WRITE))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public MediathekFolder requireReadable(AppUserDetails actor, long unitId, long folderId) {
        MediathekFolder folder = folderRepository
                .findByIdWithUnits(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Ordner nicht gefunden."));
        if (!canRead(actor, unitId, folder)) {
            throw new IllegalArgumentException("Keine Berechtigung für diesen Ordner.");
        }
        return folder;
    }

    @Transactional(readOnly = true)
    public MediathekFolder requireWritable(AppUserDetails actor, long unitId, long folderId) {
        MediathekFolder folder = requireReadable(actor, unitId, folderId);
        if (!canWrite(actor, unitId, folder)) {
            throw new IllegalArgumentException("Keine Schreibberechtigung für diesen Ordner.");
        }
        return folder;
    }

    private MediathekFolder resolveAclFolder(MediathekFolder folder) {
        MediathekFolder current = folderRepository.findByIdWithUnits(folder.getId()).orElse(folder);
        int guard = 0;
        while (current.isInheritAcl() && current.getParent() != null && guard++ < 50) {
            Long parentId = current.getParent().getId();
            current = folderRepository.findByIdWithUnits(parentId).orElse(current);
            if (current.getId().equals(parentId) && current.isInheritAcl() && current.getParent() == null) {
                break;
            }
        }
        return current;
    }
}
