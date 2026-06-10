package de.feuerwehr.manager.berichte;

import java.util.ArrayList;
import java.util.List;

public record PersonDamageDetails(
        List<PersonDamageEntry> rescued,
        List<PersonDamageEntry> injured,
        List<PersonDamageEntry> recovered,
        List<PersonDamageEntry> dead) {

    public static PersonDamageDetails empty() {
        return new PersonDamageDetails(List.of(), List.of(), List.of(), List.of());
    }

    public PersonDamageDetails normalized(int rescuedCount, int injuredCount, int recoveredCount, int deadCount) {
        return new PersonDamageDetails(
                fitList(rescued, rescuedCount),
                fitList(injured, injuredCount),
                fitList(recovered, recoveredCount),
                fitList(dead, deadCount));
    }

    private static List<PersonDamageEntry> fitList(List<PersonDamageEntry> source, int count) {
        int target = Math.max(0, count);
        List<PersonDamageEntry> items = source != null ? new ArrayList<>(source) : new ArrayList<>();
        while (items.size() < target) {
            items.add(PersonDamageEntry.empty());
        }
        if (items.size() > target) {
            return new ArrayList<>(items.subList(0, target));
        }
        return items;
    }
}
