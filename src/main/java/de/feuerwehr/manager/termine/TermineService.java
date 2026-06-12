package de.feuerwehr.manager.termine;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.PersonalService;
import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
import de.feuerwehr.manager.user.User;
import de.feuerwehr.manager.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermineService {

    private final UnitTerminRepository unitTerminRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PersonalService personalService;

    @Transactional(readOnly = true)
    public List<DienstplanTerminView> listDienstplanTermine(long unitId) {
        return unitTerminRepository.findByUnitAndCategoryWithInstructor(unitId, TermineCategory.DIENSTPLAN).stream()
                .map(this::toDienstplanView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listKnownDienstplanThemen(long unitId) {
        return unitTerminRepository.findDistinctTitlesByUnitAndCategory(unitId, TermineCategory.DIENSTPLAN);
    }

    @Transactional
    public void createDienstplanTermin(long unitId, long userId, CreateDienstplanTerminRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Bitte alle Pflichtfelder ausfüllen.");
        }
        if (request.terminDatum() == null) {
            throw new IllegalArgumentException("Bitte ein Datum angeben.");
        }
        if (request.dienstBeginn() == null) {
            throw new IllegalArgumentException("Bitte die Uhrzeit Dienstbeginn angeben.");
        }
        if (request.dienstEnde() == null) {
            throw new IllegalArgumentException("Bitte die Uhrzeit Dienstende angeben.");
        }
        String thema = trimToNull(request.thema());
        if (thema == null) {
            throw new IllegalArgumentException("Bitte ein Thema angeben.");
        }
        LocalDateTime startAt = LocalDateTime.of(request.terminDatum(), request.dienstBeginn());
        LocalDateTime endAt = LocalDateTime.of(request.terminDatum(), request.dienstEnde());
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Dienstende muss nach Dienstbeginn liegen.");
        }

        Unit unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
        User createdBy = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        Person instructor = resolveInstructor(unitId, request.instructorPersonId());

        UnitTermin termin = new UnitTermin();
        termin.setUnit(unit);
        termin.setCategory(TermineCategory.DIENSTPLAN);
        termin.setTitle(thema);
        termin.setStartAt(startAt);
        termin.setEndAt(endAt);
        termin.setInstructorPerson(instructor);
        termin.setCreatedBy(createdBy);
        unitTerminRepository.save(termin);
    }

    private Person resolveInstructor(long unitId, Long personId) {
        if (personId == null) {
            return null;
        }
        Person person = personalService.requirePerson(personId);
        if (person.getUnit() == null || person.getUnit().getId() != unitId) {
            throw new IllegalArgumentException("Ausbilder gehört nicht zu dieser Einheit.");
        }
        return person;
    }

    private DienstplanTerminView toDienstplanView(UnitTermin termin) {
        String ausbilderName = null;
        if (termin.getInstructorPerson() != null) {
            ausbilderName = termin.getInstructorPerson().anwesenheitDisplayName();
        }
        return new DienstplanTerminView(
                termin.getId(),
                termin.getStartAt().toLocalDate(),
                termin.getTitle(),
                termin.getStartAt().toLocalTime(),
                termin.getEndAt() != null ? termin.getEndAt().toLocalTime() : null,
                ausbilderName);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
