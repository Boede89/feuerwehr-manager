package de.feuerwehr.manager.settings;

import de.feuerwehr.manager.divera.TestDiveraAlarmRepository;
import de.feuerwehr.manager.personal.CourseRepository;
import de.feuerwehr.manager.personal.PersonRepository;
import de.feuerwehr.manager.personal.QualificationTypeRepository;
import de.feuerwehr.manager.unit.UnitDiveraSettingsRepository;
import de.feuerwehr.manager.unit.UnitRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestModeService {

    private final ApplicationSettingsRepository settingsRepository;
    private final PersonRepository personRepository;
    private final CourseRepository courseRepository;
    private final QualificationTypeRepository qualificationTypeRepository;
    private final UnitRepository unitRepository;
    private final UnitDiveraSettingsRepository diveraSettingsRepository;
    private final TestDiveraAlarmRepository testDiveraAlarmRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return settings().isTestModeEnabled();
    }

    /** Filterwert für fachliche Daten (Personen, Kurse, …). */
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
        entityManager
                .createQuery("DELETE FROM PersonDiveraRic r WHERE r.person.testData = true")
                .executeUpdate();
        entityManager
                .createQuery("DELETE FROM PersonCourseCompletion c WHERE c.person.testData = true")
                .executeUpdate();
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

    private ApplicationSettings createDefaultSettings() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setId(ApplicationSettings.SINGLETON_ID);
        settings.setTestModeEnabled(false);
        settings.setSmtpPort(587);
        settings.setSmtpEncryption("TLS");
        return settingsRepository.save(settings);
    }
}
