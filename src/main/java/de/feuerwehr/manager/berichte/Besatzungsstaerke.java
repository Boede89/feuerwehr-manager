package de.feuerwehr.manager.berichte;

import de.feuerwehr.manager.personal.Person;
import de.feuerwehr.manager.personal.QualificationType;
import java.util.Collection;
import java.util.List;

/** Besatzungsstärke im Format Zugführer/Gruppenführer/Mannschaft/Summe (z. B. 1/2/5/8). */
public final class Besatzungsstaerke {

    private Besatzungsstaerke() {}

    public static String format(Collection<Person> crew) {
        if (crew == null || crew.isEmpty()) {
            return "0/0/0/0";
        }
        int zf = 0;
        int gf = 0;
        int m = 0;
        for (Person person : crew) {
            switch (qualTier(person)) {
                case ZF -> zf++;
                case GF -> gf++;
                default -> m++;
            }
        }
        int sum = zf + gf + m;
        return zf + "/" + gf + "/" + m + "/" + sum;
    }

    public static String formatFromIds(List<Long> personIds, Collection<Person> allPersons) {
        if (personIds == null || personIds.isEmpty()) {
            return "0/0/0/0";
        }
        List<Person> crew = allPersons.stream().filter(p -> personIds.contains(p.getId())).toList();
        return format(crew);
    }

    public static QualTier qualTier(Person person) {
        if (person == null || person.getQualificationType() == null) {
            return QualTier.MANNSCHAFT;
        }
        String name = person.getQualificationType().getName();
        if (name == null) {
            return QualTier.MANNSCHAFT;
        }
        String lower = name.toLowerCase().trim();
        if (lower.contains("zugführer") || lower.equals("zf")) {
            return QualTier.ZF;
        }
        if (lower.contains("gruppenführer") || lower.equals("gf")) {
            return QualTier.GF;
        }
        return QualTier.MANNSCHAFT;
    }

    public enum QualTier {
        ZF,
        GF,
        MANNSCHAFT
    }
}
