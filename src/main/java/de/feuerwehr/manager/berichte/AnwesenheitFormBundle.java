package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.personal.Person;
import java.util.List;

public record AnwesenheitFormBundle(
        AttendanceReport report,
        EinsatzberichtForm form,
        KraefteFahrzeugeState kraefteState,
        String kraefteInitialJson,
        List<Person> unitPersons,
        List<String> knownStichworteDienstplan,
        List<String> knownStichworteSonstiges,
        boolean allowForeignUnitPersonnel) {}
