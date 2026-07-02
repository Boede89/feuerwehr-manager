package de.feuerwehr.manager.berichte;

import java.util.List;

public record MaterialDamageEntries(List<MaterialDamageEntry> entries) {

    public static MaterialDamageEntries empty() {
        return new MaterialDamageEntries(List.of());
    }

    public MaterialDamageEntries normalized() {
        if (entries == null || entries.isEmpty()) {
            return empty();
        }
        List<MaterialDamageEntry> items = entries.stream()
                .map(MaterialDamageEntry::normalized)
                .filter(MaterialDamageEntry::hasContent)
                .toList();
        return new MaterialDamageEntries(items);
    }
}
