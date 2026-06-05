package de.feuerwehr.manager.transfer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parst INSERT-Zeilen aus SQL-Exports der alten PHP-Feuerwehr-App. */
public final class LegacySqlInsertParser {

    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "INSERT\\s+INTO\\s+`?([a-zA-Z0-9_]+)`?\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\((.+)\\)\\s*;?",
            Pattern.CASE_INSENSITIVE);

    private LegacySqlInsertParser() {}

    public static Map<String, List<Map<String, String>>> parse(String sql) {
        Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        if (sql == null || sql.isBlank()) {
            return tables;
        }
        Matcher matcher = INSERT_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            List<String> columns = splitColumns(matcher.group(2));
            List<String> values = splitValues(matcher.group(3));
            if (columns.size() != values.size()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i), unquote(values.get(i)));
            }
            tables.computeIfAbsent(table, ignored -> new ArrayList<>()).add(row);
        }
        return tables;
    }

    private static List<String> splitColumns(String raw) {
        List<String> columns = new ArrayList<>();
        for (String part : raw.split(",")) {
            String col = part.trim().replace("`", "");
            if (!col.isEmpty()) {
                columns.add(col);
            }
        }
        return columns;
    }

    private static List<String> splitValues(String raw) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                current.append(c);
                if (c == stringChar && raw.charAt(i - 1) != '\\') {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inString = true;
                stringChar = c;
                current.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
                current.append(c);
                continue;
            }
            if (c == ')') {
                depth--;
                current.append(c);
                continue;
            }
            if (c == ',' && depth == 0) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            values.add(current.toString().trim());
        }
        return values;
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ("NULL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            return inner.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return trimmed;
    }
}
