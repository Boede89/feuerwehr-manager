package de.feuerwehr.manager.transfer;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    public static final String FORMAT = "feuerwehr-manager-database";
    public static final int VERSION = 1;

    private static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history");

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public byte[] exportSql() {
        List<String> tables = listApplicationTables();
        StringBuilder out = new StringBuilder(Math.max(4096, tables.size() * 256));
        out.append("-- Feuerwehr-Manager Database Backup\n");
        out.append("-- format: ").append(FORMAT).append('\n');
        out.append("-- version: ").append(VERSION).append('\n');
        out.append("-- exported_at: ").append(Instant.now()).append('\n');
        out.append("SET NAMES utf8mb4;\n");
        out.append("SET FOREIGN_KEY_CHECKS=0;\n");
        for (String table : tables) {
            out.append("DELETE FROM `").append(table).append("`;\n");
        }
        for (String table : tables) {
            appendTableInserts(table, out);
        }
        out.append("SET FOREIGN_KEY_CHECKS=1;\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public DatabaseImportSummary importSql(byte[] bytes) {
        String sql = new String(bytes, StandardCharsets.UTF_8);
        validateBackupHeader(sql);
        List<String> statements = splitStatements(sql);
        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger inserts = new AtomicInteger();
        try {
            jdbcTemplate.execute((java.sql.Connection connection) -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=0");
                    for (String raw : statements) {
                        String trimmed = raw.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                            continue;
                        }
                        String upper = trimmed.toUpperCase(Locale.ROOT);
                        if (upper.startsWith("SET ")) {
                            statement.execute(trimmed);
                            continue;
                        }
                        if (upper.startsWith("DELETE FROM")) {
                            statement.execute(trimmed);
                            deletes.incrementAndGet();
                            continue;
                        }
                        if (upper.startsWith("INSERT INTO")) {
                            statement.execute(trimmed);
                            inserts.incrementAndGet();
                        }
                    }
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
                return null;
            });
        } catch (Exception e) {
            throw new IllegalStateException("Import fehlgeschlagen: " + e.getMessage(), e);
        }
        return new DatabaseImportSummary(deletes.get(), inserts.get());
    }

    private List<String> listApplicationTables() {
        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("Datenbankschema konnte nicht ermittelt werden.");
        }
        return jdbcTemplate.query(
                """
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """,
                (rs, rowNum) -> rs.getString("TABLE_NAME"),
                schema)
                .stream()
                .filter(name -> !EXCLUDED_TABLES.contains(name.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private void appendTableInserts(String table, StringBuilder out) {
        jdbcTemplate.query("SELECT * FROM `" + table + "`", rs -> {
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
        });
    }

    private static String formatSqlValue(ResultSet rs, int columnIndex, int sqlType) throws SQLException {
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

    private static String formatHexBlob(byte[] bytes) {
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

    private static String quoteString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    private static void validateBackupHeader(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Leere Backup-Datei.");
        }
        if (!sql.contains("-- format: " + FORMAT)) {
            throw new IllegalArgumentException(
                    "Ungültige Backup-Datei. Bitte einen Export aus dem Feuerwehr-Manager verwenden.");
        }
    }

    private static List<String> splitStatements(String sql) {
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

    public record DatabaseImportSummary(int deletedTables, int insertedRows) {
        public String message() {
            return String.format(
                    Locale.GERMANY,
                    "Datenbank wiederhergestellt: %d Tabellen geleert, %d Datensätze importiert.",
                    deletedTables,
                    insertedRows);
        }
    }
}
