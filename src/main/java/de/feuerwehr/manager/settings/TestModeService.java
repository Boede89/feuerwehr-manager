package de.feuerwehr.manager.settings;

import de.feuerwehr.manager.atemschutz.AtemschutzCarrierRepository;
import de.feuerwehr.manager.atemschutz.AtemschutzFitnessRecordRepository;
import de.feuerwehr.manager.atemschutz.StreckeTerminRepository;
import de.feuerwehr.manager.berichte.IncidentReportRepository;
import de.feuerwehr.manager.config.StorageProperties;
import de.feuerwehr.manager.divera.TestDiveraAlarmRepository;
import de.feuerwehr.manager.personal.CourseRepository;
import de.feuerwehr.manager.personal.InstructorGroupRepository;
import de.feuerwehr.manager.personal.PersonGroupRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.QualificationTypeRepository;
import de.feuerwehr.manager.technik.RoomRepository;
import de.feuerwehr.manager.technik.VehicleRepository;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestModeService {

    private final ApplicationSettingsRepository settingsRepository;
    private final PersonGroupRepository personGroupRepository;
    private final InstructorGroupRepository instructorGroupRepository;
    private final PersonRepository personRepository;
    private final CourseRepository courseRepository;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final UnitRepository unitRepository;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final TestDiveraAlarmRepository testDiveraAlarmRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final StorageProperties storageProperties;
    private final AtemschutzFitnessRecordRepository fitnessRecordRepository;
    private final StreckeTerminRepository streckeTerminRepository;
    private final AtemschutzCarrierRepository carrierRepository;
    private final VehicleRepository vehicleRepository;
    private final RoomRepository roomRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return settings().isTestModeEnabled();
    }

    /** Kennzeichnet neu erstellte Datensätze im Testmodus. */
    @Transactional(readOnly = true)
    public boolean testDataScope() {
        return isEnabled();
    }

    @Transactional
    public void enable() {
        ApplicationSettings settings = settings();
        settings.setTestModeEnabled(true);
        settingsRepository.saveAndFlush(settings);
    }

    @Transactional
    public void disable() {
        purgeAllTestData();
        ApplicationSettings settings = settings();
        settings.setTestModeEnabled(false);
        settingsRepository.saveAndFlush(settings);
    }

    @Transactional
    public void purgeAllTestData() {
        List<Long> testReportIds = entityManager
                .createQuery("SELECT r.id FROM IncidentReport r WHERE r.testData = true", Long.class)
                .getResultList();
        for (Long reportId : testReportIds) {
            deleteReportAttachmentDirectory(reportId);
        }
        incidentReportRepository.deleteAllByTestDataTrue();

        fitnessRecordRepository.deleteAllByTestDataTrue();
        streckeTerminRepository.deleteAllByTestDataTrue();
        carrierRepository.deleteAllByTestDataTrue();
        vehicleRepository.deleteAllByTestDataTrue();
        roomRepository.deleteAllByTestDataTrue();

        entityManager
                .createQuery("DELETE FROM PersonDiveraRic r WHERE r.person.testData = true")
                .executeUpdate();
        entityManager
                .createQuery("DELETE FROM PersonCourseCompletion c WHERE c.person.testData = true")
                .executeUpdate();
        personGroupRepository.deleteAllByTestDataTrue();
        instructorGroupRepository.deleteAllByTestDataTrue();
        personRepository.deleteAllByTestDataTrue();
        courseRepository.deleteAllByTestDataTrue();
        qualificationTypeRepository.deleteAllByTestDataTrue();
        diveraSettingsRepository.deleteAllByUnitTestDataTrue();
        unitRepository.deleteAllByTestDataTrue();
        testDiveraAlarmRepository.deleteAllAlarms();
        entityManager.flush();
    }

    private ApplicationSettings settings() {
        return settingsRepository
                .findById(ApplicationSettings.SINGLETON_ID)
                .orElseGet(this::createDefaultSettings);
    }

    private void deleteReportAttachmentDirectory(long reportId) {
        Path dir = Path.of(storageProperties.getDataDir(), "incidents", String.valueOf(reportId));
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Verzeichnis wird beim nächsten Löschversuch erneut versucht
                }
            });
        } catch (IOException ignored) {
            // DB-Löschung entfernt Metadaten auch ohne Dateisystem-Bereinigung
        }
    }

    private ApplicationSettings createDefaultSettings() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setId(ApplicationSettings.SINGLETON_ID);
        settings.setTestModeEnabled(false);
        settings.setSmtpPort(587);
        settings.setSmtpEncryption("TLS");
        return settingsRepository.save(settings);
    }
}
