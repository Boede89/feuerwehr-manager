package de.feuerwehr.manager.mediathek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.security.AppUserDetails;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRole;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediathekAccessServiceTest {

    @Mock
    private MediathekFolderRepository folderRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PersonGroupRepository personGroupRepository;

    @Mock
    private TestModeService testModeService;

    @InjectMocks
    private MediathekAccessService accessService;

    private Unit unitA;
    private Unit unitB;
    private AppUserDetails userActor;
    private AppUserDetails adminActor;
    private Person person;

    @BeforeEach
    void setUp() {
        unitA = unit(1L, "Löschzug A");
        unitB = unit(2L, "Löschzug B");

        User user = new User();
        user.setId(10L);
        user.setUsername("mitglied");
        user.setDisplayName("Max Mitglied");
        user.setPasswordHash("x");
        user.setRole(UserRole.USER);
        user.setActive(true);
        user.setUnit(unitA);
        userActor = AppUserDetails.from(user);

        User admin = new User();
        admin.setId(11L);
        admin.setUsername("admin");
        admin.setDisplayName("Admin");
        admin.setPasswordHash("x");
        admin.setRole(UserRole.UNIT_ADMIN);
        admin.setActive(true);
        admin.setUnit(unitA);
        adminActor = AppUserDetails.from(admin);

        person = new Person();
        person.setId(100L);
        person.setFirstName("Max");
        person.setLastName("Mitglied");
        person.setUnit(unitA);
    }

    @Test
    void adminSeesSharedFolderInOwnUnitWithWrite() {
        MediathekFolder folder = rootFolder(50L, unitA, Set.of(unitA, unitB));
        when(folderRepository.findByIdWithUnits(50L)).thenReturn(Optional.of(folder));

        assertThat(accessService.canRead(adminActor, unitA.getId(), folder)).isTrue();
        assertThat(accessService.canWrite(adminActor, unitA.getId(), folder)).isTrue();
        assertThat(accessService.isVisibleInUnit(folder, unitB.getId())).isTrue();
    }

    @Test
    void folderNotVisibleInUnrelatedUnit() {
        MediathekFolder folder = rootFolder(51L, unitA, Set.of(unitA));
        assertThat(accessService.isVisibleInUnit(folder, unitB.getId())).isFalse();
        assertThat(accessService.canRead(adminActor, unitB.getId(), folder)).isFalse();
    }

    @Test
    void multiUnitFolderVisibleForBothUnits() {
        MediathekFolder folder = rootFolder(52L, unitA, Set.of(unitA, unitB));
        assertThat(accessService.isVisibleInUnit(folder, unitA.getId())).isTrue();
        assertThat(accessService.isVisibleInUnit(folder, unitB.getId())).isTrue();
    }

    @Test
    void userNeedsAclEntryForRead() {
        when(testModeService.isEnabled()).thenReturn(false);
        when(personRepository.findActiveByUserIdAndUnitId(10L, 1L, false)).thenReturn(Optional.of(person));
        when(personGroupRepository.findGroupIdsByMemberId(100L)).thenReturn(Set.of());

        MediathekFolder folder = rootFolder(53L, unitA, Set.of(unitA));
        folder.setInheritAcl(false);
        when(folderRepository.findByIdWithUnits(53L)).thenReturn(Optional.of(folder));
        when(folderRepository.findByIdWithAcl(53L)).thenReturn(Optional.of(folder));

        assertThat(accessService.canRead(userActor, unitA.getId(), folder)).isFalse();

        MediathekFolderAcl acl = new MediathekFolderAcl();
        acl.setFolder(folder);
        acl.setPerson(person);
        acl.setAccessLevel(MediathekAccessLevel.READ);
        folder.setAclEntries(List.of(acl));

        assertThat(accessService.canRead(userActor, unitA.getId(), folder)).isTrue();
        assertThat(accessService.canWrite(userActor, unitA.getId(), folder)).isFalse();
    }

    @Test
    void childInheritsAclFromParentWhenInheritEnabled() {
        when(testModeService.isEnabled()).thenReturn(false);
        when(personRepository.findActiveByUserIdAndUnitId(10L, 1L, false)).thenReturn(Optional.of(person));
        when(personGroupRepository.findGroupIdsByMemberId(100L)).thenReturn(Set.of());

        MediathekFolder parent = rootFolder(60L, unitA, Set.of(unitA));
        parent.setInheritAcl(false);
        MediathekFolderAcl acl = new MediathekFolderAcl();
        acl.setFolder(parent);
        acl.setPerson(person);
        acl.setAccessLevel(MediathekAccessLevel.WRITE);
        parent.setAclEntries(List.of(acl));

        MediathekFolder child = new MediathekFolder();
        child.setId(61L);
        child.setName("Unterordner");
        child.setOwnerUnit(unitA);
        child.setParent(parent);
        child.setInheritAcl(true);
        child.setSharedUnits(new LinkedHashSet<>(Set.of(unitA)));

        when(folderRepository.findByIdWithUnits(61L)).thenReturn(Optional.of(child));
        when(folderRepository.findByIdWithUnits(60L)).thenReturn(Optional.of(parent));
        when(folderRepository.findByIdWithAcl(60L)).thenReturn(Optional.of(parent));

        assertThat(accessService.canRead(userActor, unitA.getId(), child)).isTrue();
        assertThat(accessService.canWrite(userActor, unitA.getId(), child)).isTrue();
    }

    @Test
    void childOwnAclOverridesInheritance() {
        when(testModeService.isEnabled()).thenReturn(false);
        when(personRepository.findActiveByUserIdAndUnitId(10L, 1L, false)).thenReturn(Optional.of(person));
        when(personGroupRepository.findGroupIdsByMemberId(100L)).thenReturn(Set.of());

        MediathekFolder parent = rootFolder(70L, unitA, Set.of(unitA));
        parent.setInheritAcl(false);
        MediathekFolderAcl parentAcl = new MediathekFolderAcl();
        parentAcl.setFolder(parent);
        parentAcl.setPerson(person);
        parentAcl.setAccessLevel(MediathekAccessLevel.WRITE);
        parent.setAclEntries(List.of(parentAcl));

        MediathekFolder child = new MediathekFolder();
        child.setId(71L);
        child.setName("Geschützt");
        child.setOwnerUnit(unitA);
        child.setParent(parent);
        child.setInheritAcl(false);
        child.setSharedUnits(new LinkedHashSet<>(Set.of(unitA)));
        child.setAclEntries(List.of());

        when(folderRepository.findByIdWithUnits(71L)).thenReturn(Optional.of(child));
        when(folderRepository.findByIdWithAcl(71L)).thenReturn(Optional.of(child));

        assertThat(accessService.canRead(userActor, unitA.getId(), child)).isFalse();
    }

    @Test
    void groupMembershipGrantsAccess() {
        when(testModeService.isEnabled()).thenReturn(false);
        when(personRepository.findActiveByUserIdAndUnitId(10L, 1L, false)).thenReturn(Optional.of(person));
        when(personGroupRepository.findGroupIdsByMemberId(100L)).thenReturn(Set.of(5L));

        PersonGroup group = new PersonGroup();
        group.setId(5L);
        group.setName("Ausbildung");

        MediathekFolder folder = rootFolder(80L, unitA, Set.of(unitA));
        folder.setInheritAcl(false);
        MediathekFolderAcl acl = new MediathekFolderAcl();
        acl.setFolder(folder);
        acl.setGroup(group);
        acl.setAccessLevel(MediathekAccessLevel.READ);
        folder.setAclEntries(List.of(acl));

        when(folderRepository.findByIdWithUnits(80L)).thenReturn(Optional.of(folder));
        when(folderRepository.findByIdWithAcl(80L)).thenReturn(Optional.of(folder));

        assertThat(accessService.canRead(userActor, unitA.getId(), folder)).isTrue();
        assertThat(accessService.canWrite(userActor, unitA.getId(), folder)).isFalse();
    }

    private static Unit unit(long id, String name) {
        Unit unit = new Unit();
        unit.setId(id);
        unit.setName(name);
        return unit;
    }

    private static MediathekFolder rootFolder(long id, Unit owner, Set<Unit> shared) {
        MediathekFolder folder = new MediathekFolder();
        folder.setId(id);
        folder.setName("Root-" + id);
        folder.setOwnerUnit(owner);
        folder.setInheritAcl(false);
        folder.setSharedUnits(new LinkedHashSet<>(shared));
        folder.setAclEntries(List.of());
        return folder;
    }
}
