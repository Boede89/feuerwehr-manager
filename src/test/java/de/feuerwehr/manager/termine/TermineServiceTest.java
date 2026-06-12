package de.feuerwehr.manager.termine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @InjectMocks
    private TermineService termineService;

    @Test
    void createDienstplanTerminRejectsEndBeforeStart() {
        var request = new CreateDienstplanTerminRequest(
                LocalDate.of(2026, 6, 2),
                "Atemschutz",
                LocalTime.of(20, 0),
                LocalTime.of(19, 0),
                null);

        assertThatThrownBy(() -> termineService.createDienstplanTermin(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dienstende");
    }
}
