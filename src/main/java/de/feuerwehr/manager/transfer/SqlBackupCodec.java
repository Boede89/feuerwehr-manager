package de.feuerwehr.manager.transfer;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

final class SqlBackupCodec {

    private SqlBackupCodec() {}

    static void appendQueryInserts(
            JdbcTemplate jdbcTemplate, String table, String selectSql, Object[] params, StringBuilder out) {
        jdbcTemplate.query(selectSql, (RowCallbackHandler) rs -> appendRowInsert(table, rs, out), params);
    }

    static void appendRowInsert(String table, ResultSet rs, StringBuilder out) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append('`').append(meta.getColumnName(i)).append('`');
            values.append(formatSqlValue(rs, i, meta.getColumnType(i)));
        }
        out.append("INSERT INTO `")
                .append(table)
                .append("` (")
                .append(columns)
                .append(") VALUES (")
                .append(values)
                .append(");\n");
    }

    static void appendRowInsert(ResultSet rs, StringBuilder out) throws SQLException {
        String table = rs.getMetaData().getTableName(1);
        if (table == null || table.isBlank()) {
            return;
        }
        appendRowInsert(table, rs, out);
    }

    static void appendReplaceFromQuery(JdbcTemplate jdbcTemplate, String table, String selectSql, Object[] params, StringBuilder out) {
        jdbcTemplate.query(
                selectSql,
                (RowCallbackHandler) rs -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    StringBuilder columns = new StringBuilder();
                    StringBuilder values = new StringBuilder();
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            columns.append(", ");
                            values.append(", ");
                        }
                        columns.append('`').append(meta.getColumnName(i)).append('`');
                        values.append(formatSqlValue(rs, i, meta.getColumnType(i)));
                    }
                    out.append("REPLACE INTO `")
                            .append(table)
                            .append("` (")
                            .append(columns)
                            .append(") VALUES (")
                            .append(values)
                            .append(");\n");
                },
                params);
    }

    static void executeScript(JdbcTemplate jdbcTemplate, String sql, Consumer<String> onUnsupported) {
        for (String raw : splitStatements(sql)) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("SET ")
                    || upper.startsWith("DELETE FROM")
                    || upper.startsWith("INSERT INTO")
                    || upper.startsWith("REPLACE INTO")) {
                jdbcTemplate.execute(trimmed);
                continue;
            }
            if (onUnsupported != null) {
                onUnsupported.accept(trimmed);
            }
        }
    }

    static String formatSqlValue(ResultSet rs, int columnIndex, int sqlType) throws SQLException {
        Object value = rs.getObject(columnIndex);
        if (value == null || rs.wasNull()) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            if (sqlType == Types.BIT || sqlType == Types.BOOLEAN) {
                return bytes.length > 0 && bytes[0] != 0 ? "1" : "0";
            }
            return formatHexBlob(bytes);
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                    Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> value.toString();
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> formatHexBlob(rs.getBytes(columnIndex));
            default -> quoteString(String.valueOf(value));
        };
    }

    static String formatHexBlob(byte[] bytes) {
        if (bytes == null) {
            return "NULL";
        }
        StringBuilder hex = new StringBuilder(bytes.length * 2 + 2);
        hex.append("0x");
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    static String quoteString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                current.append(c);
                if (c == stringChar && sql.charAt(i - 1) != '\\') {
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
            if (c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    static Long parseHeaderLong(String sql, String key) {
        String prefix = "-- " + key + ":";
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return Long.parseLong(trimmed.substring(prefix.length()).trim());
            }
        }
        return null;
    }

    static String parseHeaderString(String sql, String key) {
        String prefix = "-- " + key + ":";
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    static void validateFormat(String sql, String expectedFormat) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Leere Backup-Datei.");
        }
        if (!sql.contains("-- format: " + expectedFormat)) {
            throw new IllegalArgumentException("Ungültige Backup-Datei für diesen Import.");
        }
    }
}
