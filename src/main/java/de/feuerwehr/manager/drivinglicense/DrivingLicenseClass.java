package de.feuerwehr.manager.drivinglicense;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Relevante Fahrerlaubnisklassen für den Feuerwehr-Einsatz. */
public enum DrivingLicenseClass {
    A("A"),
    B("B"),
    BE("BE"),
    C1("C1"),
    C1E("C1E"),
    C("C"),
    CE("CE");

    private final String code;

    DrivingLicenseClass(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static List<DrivingLicenseClass> all() {
        return List.of(values());
    }

    public static Set<DrivingLicenseClass> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<DrivingLicenseClass> result = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part == null ? "" : part.trim().toUpperCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            for (DrivingLicenseClass value : values()) {
                if (value.code.equals(trimmed)) {
                    result.add(value);
                    break;
                }
            }
        }
        return result;
    }

    public static String toCsv(Iterable<DrivingLicenseClass> classes) {
        if (classes == null) {
            return null;
        }
        String joined = java.util.stream.StreamSupport.stream(classes.spliterator(), false)
                .map(DrivingLicenseClass::code)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    public static String toCsvFromCodes(String[] codes) {
        if (codes == null || codes.length == 0) {
            return null;
        }
        return toCsv(parseCsv(Arrays.stream(codes).collect(Collectors.joining(","))));
    }
}
