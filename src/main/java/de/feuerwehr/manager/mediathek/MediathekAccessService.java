package de.feuerwehr.manager.mediathek;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import java.util.HashSet;
import java.util.List;
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
        Optional<Person> personOpt = linkedPerson(actor, unitId);
        if (personOpt.isEmpty()) {
            return Optional.empty();
        }
        long personId = personOpt.get().getId();
        Set<Long> groupIds = new HashSet<>(personGroupRepository.findGroupIdsByMemberId(personId));
        MediathekFolder aclFolder = resolveAclFolder(folder);
        List<MediathekFolderAcl> entries =
                folderRepository.findByIdWithAcl(aclFolder.getId()).map(MediathekFolder::getAclEntries).orElse(List.of());
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        MediathekAccessLevel best = null;
        for (MediathekFolderAcl entry : entries) {
            boolean match = false;
            if (entry.getPerson() != null && entry.getPerson().getId().equals(personId)) {
                match = true;
            } else if (entry.getGroup() != null && groupIds.contains(entry.getGroup().getId())) {
                match = true;
            }
            if (!match) {
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
