package de.feuerwehr.manager.termine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonGroup;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonalInstructorGroupService;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.settings.TestModeService;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermineServiceTest {

    @Mock
    private UnitTerminRepository unitTerminRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonalService personalService;

    @Mock
    private PersonGroupRepository personGroupRepository;

    @Mock
    private TestModeService testModeService;

    @Mock
    private PersonalInstructorGroupService personalInstructorGroupService;

    @InjectMocks
    private TermineService termineService;

    @Test
    void createDienstplanTerminRejectsEndBeforeStart() {
        var request = new CreateDienstplanTerminRequest(
                LocalDate.of(2026, 6, 2),
                "Atemschutz",
                LocalTime.of(20, 0),
                LocalTime.of(19, 0),
                List.of(),
                true,
                List.of(),
                List.of());

        assertThatThrownBy(() -> termineService.createDienstplanTermin(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dienstende");
    }

    @Test
    void listMyTermineMapsRepositoryResults() {
        Person person = new Person();
        person.setId(7L);
        UnitTermin termin = new UnitTermin();
        termin.setId(3L);
        termin.setCategory(TermineCategory.DIENSTPLAN);
        termin.setTitle("Atemschutz");
        termin.setStartAt(LocalDateTime.of(2026, 6, 2, 19, 0));
        termin.setEndAt(LocalDateTime.of(2026, 6, 2, 22, 0));
        termin.setAudienceAll(true);
        when(unitTerminRepository.findMineByUnitAndPerson(1L, 7L)).thenReturn(List.of(termin));

        List<MeineTerminView> result = termineService.listMyTermine(1L, 7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).thema()).isEqualTo("Atemschutz");
        assertThat(result.get(0).categoryLabel()).isEqualTo("Dienstplan");
    }

    @Test
    void listMyTermineIncludesGroupMembershipFromRepositoryQuery() {
        Person person = new Person();
        person.setId(5L);
        PersonGroup group = new PersonGroup();
        group.setId(2L);
        group.setName("Gruppe 1");
        group.setMembers(List.of(person));
        UnitTermin groupTermin = new UnitTermin();
        groupTermin.setId(8L);
        groupTermin.setCategory(TermineCategory.DIENSTPLAN);
        groupTermin.setTitle("Technische Hilfe");
        groupTermin.setStartAt(LocalDateTime.of(2026, 7, 1, 19, 0));
        groupTermin.setEndAt(LocalDateTime.of(2026, 7, 1, 22, 0));
        groupTermin.setAudienceAll(false);
        groupTermin.setAssignedGroups(new LinkedHashSet<>(Set.of(group)));
        when(unitTerminRepository.findMineByUnitAndPerson(1L, 5L)).thenReturn(List.of(groupTermin));

        List<MeineTerminView> result = termineService.listMyTermine(1L, 5L);

        assertThat(result).extracting(MeineTerminView::thema).containsExactly("Technische Hilfe");
    }
}
