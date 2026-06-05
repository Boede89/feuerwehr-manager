package de.feuerwehr.manager.transfer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
            SqlBackupCodec.appendQueryInserts(jdbcTemplate, table, "SELECT * FROM `" + table + "`", new Object[] {}, out);
        }
        out.append("SET FOREIGN_KEY_CHECKS=1;\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public DatabaseImportSummary importSql(byte[] bytes) {
        String sql = new String(bytes, StandardCharsets.UTF_8);
        SqlBackupCodec.validateFormat(sql, FORMAT);
        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger inserts = new AtomicInteger();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        SqlBackupCodec.executeScript(
                jdbcTemplate,
                sql,
                statement -> {
                    String upper = statement.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("DELETE FROM")) {
                        deletes.incrementAndGet();
                    } else if (upper.startsWith("INSERT INTO") || upper.startsWith("REPLACE INTO")) {
                        inserts.incrementAndGet();
                    }
                });
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
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

    public record DatabaseImportSummary(int deletedTables, int insertedRows) {
        public String message() {
            return String.format(
                    Locale.GERMANY,
                    "Datenbank wiederhergestellt: %d Tabellen geleert, %d Datensätze importiert.",
                    deletedTables,
                    insertedRows);
        }

        public String unitMessage(String unitName) {
            return String.format(
                    Locale.GERMANY,
                    "Einheit \"%s\" wiederhergestellt: %d Datensätze importiert.",
                    unitName,
                    insertedRows);
        }
    }
}
