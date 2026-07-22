package de.feuerwehr.manager.termine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.feuerwehr.manager.berichte.AnwesenheitslisteTerminSyncService;
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

    @Mock
    private AnwesenheitslisteTerminSyncService anwesenheitslisteTerminSyncService;

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
    void listSonstigesTermineUsesSonstigesCategory() {
        when(unitTerminRepository.findByUnitAndCategoryWithInstructor(1L, TermineCategory.SONSTIGES))
                .thenReturn(List.of());

        termineService.listSonstigesTermine(1L);

        org.mockito.Mockito.verify(unitTerminRepository)
                .findByUnitAndCategoryWithInstructor(1L, TermineCategory.SONSTIGES);
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

    @Test
    void listUpcomingDashboardTermineFiltersPastAndLimitsResults() {
        UnitTermin past = new UnitTermin();
        past.setId(1L);
        past.setCategory(TermineCategory.DIENSTPLAN);
        past.setTitle("Vergangen");
        past.setStartAt(LocalDateTime.of(2020, 1, 1, 19, 0));
        past.setEndAt(LocalDateTime.of(2020, 1, 1, 22, 0));
        past.setAudienceAll(true);

        UnitTermin upcoming = new UnitTermin();
        upcoming.setId(2L);
        upcoming.setCategory(TermineCategory.SONSTIGES);
        upcoming.setTitle("Zukunft");
        upcoming.setStartAt(LocalDateTime.now().plusDays(2).withHour(18).withMinute(30).withSecond(0).withNano(0));
        upcoming.setEndAt(upcoming.getStartAt().plusHours(2));
        upcoming.setAudienceAll(true);

        when(unitTerminRepository.findMineByUnitAndPerson(1L, 7L)).thenReturn(List.of(past, upcoming));

        List<DashboardTerminWidgetView> result = termineService.listUpcomingDashboardTermine(1L, 7L, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).terminId()).isEqualTo(2L);
        assertThat(result.get(0).title()).isEqualTo("Zukunft");
        assertThat(result.get(0).categoryLabel()).isEqualTo("Sonstiges");
        assertThat(result.get(0).time()).isEqualTo("18:30");
        assertThat(result.get(0).today()).isFalse();
    }
}
