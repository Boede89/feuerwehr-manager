package de.feuerwehr.manager.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.feuerwehr.manager.mail.AccountMailService;
import de.feuerwehr.manager.settings.ApplicationSettings;
import de.feuerwehr.manager.settings.GlobalSettingsService;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import de.feuerwehr.manager.web.dto.BugReportRequest;
import de.feuerwehr.manager.web.dto.BugReportResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BugReportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountMailService accountMailService;

    @Mock
    private GlobalSettingsService globalSettingsService;

    @InjectMocks
    private BugReportService bugReportService;

    @Test
    void submitRejectsMissingEmail() {
        BugReportResult result = bugReportService.submit(new BugReportRequest(
                "Max Mustermann", "", "Reservierung", "Beschreibung mit genug Text.", "https://example.test/login"));

        assertFalse(result.success());
        verify(accountMailService, never()).sendGlobalPlainMail(anyString(), anyString(), anyString());
    }

    @Test
    void submitRejectsMissingArea() {
        BugReportResult result = bugReportService.submit(new BugReportRequest(
                "Max Mustermann", "max@example.test", "", "Beschreibung mit genug Text.", "https://example.test/login"));

        assertFalse(result.success());
        verify(accountMailService, never()).sendGlobalPlainMail(anyString(), anyString(), anyString());
    }

    @Test
    void submitRejectsShortDescription() {
        BugReportResult result = bugReportService.submit(new BugReportRequest(
                "Max Mustermann", "", "Reservierung", "kurz", "https://example.test/login"));

        assertFalse(result.success());
        verify(accountMailService, never()).sendGlobalPlainMail(anyString(), anyString(), anyString());
    }

    @Test
    void submitSendsMailToSuperAdmins() {
        User admin = new User();
        admin.setLoginEmail("admin@example.test");

        ApplicationSettings settings = new ApplicationSettings();
        settings.setFfName("Test-FF");

        when(accountMailService.canSendGlobalMail()).thenReturn(true);
        when(userRepository.findActiveSuperAdminsWithEmail()).thenReturn(List.of(admin));
        when(globalSettingsService.get()).thenReturn(settings);
        when(accountMailService.sendGlobalPlainMail(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        BugReportResult result = bugReportService.submit(new BugReportRequest(
                "Max Mustermann",
                "max@example.test",
                "Reservierung",
                "Beim Speichern erscheint ein Fehler 500.",
                "https://example.test/reservieren"));

        assertTrue(result.success());
        verify(accountMailService).sendGlobalPlainMail(anyString(), anyString(), anyString());
    }
}
