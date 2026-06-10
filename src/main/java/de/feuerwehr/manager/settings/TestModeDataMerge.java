package de.feuerwehr.manager.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Zusammenführen von Produktiv- und Test-Schattenkopien für Listen im Testmodus. */
public final class TestModeDataMerge {

    private TestModeDataMerge() {}

    public static <T> List<T> mergeByProductionSource(
            List<T> production,
            List<T> testRows,
            Function<T, Long> sourceIdExtractor,
            Function<T, Long> idExtractor,
            Comparator<T> comparator) {
        Map<Long, T> shadowsBySource = testRows.stream()
                .filter(row -> sourceIdExtractor.apply(row) != null)
                .collect(Collectors.toMap(sourceIdExtractor, Function.identity(), (a, b) -> a));
        List<T> testOnly =
                testRows.stream().filter(row -> sourceIdExtractor.apply(row) == null).toList();
        List<T> merged = new ArrayList<>();
        for (T prod : production) {
            merged.add(shadowsBySource.getOrDefault(idExtractor.apply(prod), prod));
        }
        merged.addAll(testOnly);
        merged.sort(comparator);
        return merged;
    }
}
