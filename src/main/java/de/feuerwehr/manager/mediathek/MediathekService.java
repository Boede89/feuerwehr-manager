package de.feuerwehr.manager.mediathek;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.PersonalGroupService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.personal.QualificationType;
import de.feuerwehr.manager.personal.QualificationTypeRepository;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.unit.UnitService;
import de.feuerwehr.manager.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediathekService {

    private final MediathekFolderRepository folderRepository;
    private final MediathekFileRepository fileRepository;
    private final MediathekFolderAclRepository aclRepository;
    private final MediathekAccessService accessService;
    private final MediathekStorageService storageService;
    private final UnitRepository unitRepository;
    private final UnitService unitService;
    private final PersonRepository personRepository;
    private final PersonGroupRepository personGroupRepository;
    private final PersonalGroupService personalGroupService;
    private final PersonalService personalService;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final UserRepository userRepository;
    private final TestModeService testModeService;

    @Transactional(readOnly = true)
    public FolderView openFolder(AppUserDetails actor, long unitId, Long folderId) {
        boolean admin = actor.getRole().isAdminLevel();
        if (folderId == null || folderId <= 0) {
            List<MediathekFolder> roots = folderRepository.findRootFoldersVisibleInUnit(unitId).stream()
                    .filter(f -> accessService.canRead(actor, unitId, f))
                    .toList();
            return new FolderView(null, List.of(), roots, List.of(), admin, admin);
        }
        MediathekFolder folder = accessService.requireReadable(actor, unitId, folderId);
        List<MediathekFolder> children = folderRepository.findChildrenVisibleInUnit(folderId, unitId).stream()
                .filter(f -> accessService.canRead(actor, unitId, f))
                .toList();
        List<MediathekFile> files = fileRepository.findByFolderIdOrderByOriginalNameAsc(folderId);
        boolean canWrite = accessService.canWrite(actor, unitId, folder);
        return new FolderView(folder, breadcrumb(folder), children, files, canWrite, admin || canWrite);
    }

    @Transactional
    public MediathekFolder createFolder(
            AppUserDetails actor,
            long unitId,
            Long parentId,
            String name,
            List<Long> sharedUnitIds,
            boolean inheritAcl) {
        String cleaned = requireName(name);
        Unit owner = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        MediathekFolder folder = new MediathekFolder();
        folder.setOwnerUnit(owner);
        folder.setName(cleaned);
        folder.setSortOrder(0);
        if (parentId != null && parentId > 0) {
            MediathekFolder parent = accessService.requireWritable(actor, unitId, parentId);
            folder.setParent(parent);
            folder.setInheritAcl(inheritAcl);
            folder.setSharedUnits(new LinkedHashSet<>(
                    parent.getSharedUnits() == null || parent.getSharedUnits().isEmpty()
                            ? Set.of(owner)
                            : parent.getSharedUnits()));
            if (!folder.getSharedUnits().contains(owner)) {
                folder.getSharedUnits().add(owner);
            }
        } else {
            if (!actor.getRole().isAdminLevel()) {
                throw new IllegalArgumentException("Nur Admins können Root-Ordner anlegen.");
            }
            folder.setInheritAcl(false);
            folder.setSharedUnits(resolveSharedUnits(actor, owner, sharedUnitIds));
        }
        return folderRepository.save(folder);
    }

    @Transactional
    public MediathekFolder updateFolderUnits(AppUserDetails actor, long unitId, long folderId, List<Long> sharedUnitIds) {
        if (!actor.getRole().isAdminLevel()) {
            throw new IllegalArgumentException("Einheiten-Zuordnung nur für Admins.");
        }
        MediathekFolder folder = accessService.requireWritable(actor, unitId, folderId);
        if (folder.getParent() != null) {
            throw new IllegalArgumentException("Einheiten nur am Root-Ordner zuordnen.");
        }
        Set<Unit> units = resolveSharedUnits(actor, folder.getOwnerUnit(), sharedUnitIds);
        folder.setSharedUnits(units);
        MediathekFolder saved = folderRepository.save(folder);
        cascadeSharedUnits(saved.getId(), units);
        return saved;
    }

    @Transactional
    public MediathekFolder renameFolder(AppUserDetails actor, long unitId, long folderId, String name) {
        MediathekFolder folder = accessService.requireWritable(actor, unitId, folderId);
        folder.setName(requireName(name));
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(AppUserDetails actor, long unitId, long folderId) {
        MediathekFolder folder = accessService.requireWritable(actor, unitId, folderId);
        deleteFolderRecursive(folder);
    }

    @Transactional
    public void replaceAcl(
            AppUserDetails actor,
            long unitId,
            long folderId,
            boolean inheritAcl,
            List<AclInput> entries) {
        accessService.requireWritable(actor, unitId, folderId);
        MediathekFolder folder = folderRepository
                .findByIdWithAcl(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Ordner nicht gefunden."));
        if (folder.getParent() == null) {
            inheritAcl = false;
        }
        folder.setInheritAcl(inheritAcl);
        folder.getAclEntries().clear();
        if (!inheritAcl && entries != null) {
            for (AclInput input : entries) {
                if (input == null || input.level() == null) {
                    continue;
                }
                MediathekFolderAcl acl = new MediathekFolderAcl();
                acl.setFolder(folder);
                acl.setAccessLevel(input.level());
                if (input.personId() != null && input.personId() > 0) {
                    acl.setPerson(personalService.requirePerson(input.personId()));
                } else if (input.groupId() != null && input.groupId() > 0) {
                    PersonGroup group = personGroupRepository
                            .findById(input.groupId())
                            .orElseThrow(() -> new IllegalArgumentException("Gruppe nicht gefunden."));
                    acl.setGroup(group);
                } else if (input.qualificationTypeId() != null && input.qualificationTypeId() > 0) {
                    QualificationType qualification = qualificationTypeRepository
                            .findById(input.qualificationTypeId())
                            .orElseThrow(() -> new IllegalArgumentException("Dienstgrad nicht gefunden."));
                    acl.setQualificationType(qualification);
                } else {
                    continue;
                }
                folder.getAclEntries().add(acl);
            }
        }
        folderRepository.save(folder);
    }

    @Transactional
    public MediathekFile upload(AppUserDetails actor, long unitId, long folderId, MultipartFile file) {
        MediathekFolder folder = accessService.requireWritable(actor, unitId, folderId);
        MediathekStorageService.StoredUpload stored = storageService.store(folderId, file);
        MediathekFile entity = new MediathekFile();
        entity.setFolder(folder);
        entity.setOriginalName(stored.originalName());
        entity.setStoredName(stored.storedName());
        entity.setMimeType(stored.mimeType());
        entity.setFileSize(stored.fileSize());
        if (actor != null) {
            userRepository.findById(actor.getUserId()).ifPresent(entity::setUploadedBy);
            entity.setUploadedByDisplayName(actor.getDisplayName());
        }
        return fileRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public FileDownload download(AppUserDetails actor, long unitId, long fileId) {
        MediathekFile file = fileRepository
                .findByIdWithFolder(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Datei nicht gefunden."));
        accessService.requireReadable(actor, unitId, file.getFolder().getId());
        Resource resource = storageService.load(file.getFolder().getId(), file.getStoredName());
        return new FileDownload(resource, file.getOriginalName(), file.getMimeType(), file.getFileSize());
    }

    @Transactional
    public void deleteFile(AppUserDetails actor, long unitId, long fileId) {
        MediathekFile file = fileRepository
                .findByIdWithFolder(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Datei nicht gefunden."));
        accessService.requireWritable(actor, unitId, file.getFolder().getId());
        storageService.deleteFile(file.getFolder().getId(), file.getStoredName());
        fileRepository.delete(file);
    }

    @Transactional
    public AclFormData loadAclForm(AppUserDetails actor, long unitId, long folderId) {
        MediathekFolder folder = accessService.requireWritable(actor, unitId, folderId);
        cleanupAnonymizedAclEntries();
        MediathekFolder withAcl = folderRepository.findByIdWithAcl(folderId).orElse(folder);
        MediathekFolder withUnits = folderRepository.findByIdWithUnits(folderId).orElse(folder);
        List<Unit> units = withUnits.getSharedUnits() == null || withUnits.getSharedUnits().isEmpty()
                ? List.of(withUnits.getOwnerUnit())
                : new ArrayList<>(withUnits.getSharedUnits());
        List<Person> persons = new ArrayList<>();
        List<PersonGroup> groups = new ArrayList<>();
        List<QualificationType> qualifications = new ArrayList<>();
        boolean test = testModeService.isEnabled();
        Set<Long> seenPersons = new LinkedHashSet<>();
        Set<Long> seenGroups = new LinkedHashSet<>();
        Set<Long> seenQualifications = new LinkedHashSet<>();
        for (Unit u : units) {
            for (Person p : personRepository.findActiveByUnitId(u.getId(), test)) {
                if (seenPersons.add(p.getId())) {
                    persons.add(p);
                }
            }
            for (PersonGroup g : personalGroupService.listGroups(u.getId())) {
                if (seenGroups.add(g.getId())) {
                    groups.add(g);
                }
            }
            for (QualificationType q : personalService.listQualificationTypes(u.getId(), true)) {
                if (seenQualifications.add(q.getId())) {
                    qualifications.add(q);
                }
            }
        }
        List<MediathekFolderAcl> ownEntries = validAclEntries(withAcl.getAclEntries());
        List<MediathekFolderAcl> inheritedEntries = List.of();
        String inheritedFromName = null;
        if (withAcl.isInheritAcl() && withAcl.getParent() != null) {
            MediathekFolder source = resolveEffectiveAclFolder(withAcl);
            if (source != null && !source.getId().equals(withAcl.getId())) {
                MediathekFolder sourceAcl = folderRepository.findByIdWithAcl(source.getId()).orElse(source);
                inheritedEntries = validAclEntries(sourceAcl.getAclEntries());
                inheritedFromName = sourceAcl.getName();
            }
        }
        return new AclFormData(
                withAcl,
                ownEntries,
                inheritedEntries,
                inheritedFromName,
                persons,
                groups,
                qualifications,
                units);
    }

    private void cleanupAnonymizedAclEntries() {
        aclRepository.deleteWherePersonAnonymized();
    }

    private static List<MediathekFolderAcl> validAclEntries(List<MediathekFolderAcl> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream().filter(MediathekService::isValidAclEntry).toList();
    }

    private static boolean isValidAclEntry(MediathekFolderAcl entry) {
        if (entry == null) {
            return false;
        }
        if (entry.getPerson() != null) {
            return entry.getPerson().getAnonymizedAt() == null;
        }
        return entry.getGroup() != null || entry.getQualificationType() != null;
    }

    private MediathekFolder resolveEffectiveAclFolder(MediathekFolder folder) {
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

    @Transactional(readOnly = true)
    public List<Unit> listAssignableUnits(AppUserDetails actor) {
        return unitService.findActiveOrdered(actor);
    }

    private void cascadeSharedUnits(long parentId, Set<Unit> units) {
        for (MediathekFolder child : folderRepository.findByParentId(parentId)) {
            MediathekFolder loaded = folderRepository.findByIdWithUnits(child.getId()).orElse(child);
            loaded.setSharedUnits(new LinkedHashSet<>(units));
            folderRepository.save(loaded);
            cascadeSharedUnits(loaded.getId(), units);
        }
    }

    private void deleteFolderRecursive(MediathekFolder folder) {
        for (MediathekFolder child : folderRepository.findByParentId(folder.getId())) {
            deleteFolderRecursive(child);
        }
        for (MediathekFile file : fileRepository.findByFolderIdOrderByOriginalNameAsc(folder.getId())) {
            storageService.deleteFile(folder.getId(), file.getStoredName());
            fileRepository.delete(file);
        }
        storageService.deleteFolderDirectory(folder.getId());
        folderRepository.delete(folder);
    }

    private Set<Unit> resolveSharedUnits(AppUserDetails actor, Unit owner, List<Long> sharedUnitIds) {
        Set<Unit> units = new LinkedHashSet<>();
        units.add(owner);
        if (sharedUnitIds == null) {
            return units;
        }
        List<Unit> accessible = unitService.findActiveOrdered(actor);
        Set<Long> accessibleIds = new LinkedHashSet<>();
        accessible.forEach(u -> accessibleIds.add(u.getId()));
        for (Long id : sharedUnitIds) {
            if (id == null || id <= 0 || id.equals(owner.getId())) {
                continue;
            }
            if (!accessibleIds.contains(id) && !actor.getRole().isSuperAdmin()) {
                throw new IllegalArgumentException("Keine Berechtigung für Einheit " + id + ".");
            }
            unitRepository.findById(id).ifPresent(units::add);
        }
        return units;
    }

    private List<MediathekFolder> breadcrumb(MediathekFolder folder) {
        List<MediathekFolder> chain = new ArrayList<>();
        MediathekFolder current = folder;
        int guard = 0;
        while (current != null && guard++ < 50) {
            chain.add(0, current);
            if (current.getParent() == null || current.getParent().getId() == null) {
                break;
            }
            current = folderRepository.findByIdWithUnits(current.getParent().getId()).orElse(null);
        }
        return chain;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Ordnernamen angeben.");
        }
        String cleaned = name.trim();
        if (cleaned.length() > 255) {
            throw new IllegalArgumentException("Ordnername zu lang.");
        }
        return cleaned;
    }

    public record FolderView(
            MediathekFolder folder,
            List<MediathekFolder> breadcrumb,
            List<MediathekFolder> children,
            List<MediathekFile> files,
            boolean canWrite,
            boolean canManageAcl) {}

    public record AclInput(Long personId, Long groupId, Long qualificationTypeId, MediathekAccessLevel level) {}

    public record AclFormData(
            MediathekFolder folder,
            List<MediathekFolderAcl> entries,
            List<MediathekFolderAcl> inheritedEntries,
            String inheritedFromName,
            List<Person> persons,
            List<PersonGroup> groups,
            List<QualificationType> qualifications,
            List<Unit> units) {}

    public record FileDownload(Resource resource, String filename, String mimeType, long fileSize) {}
}
