package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V37__AtemschutzCourseSelection extends BaseJavaMigration {

    @Override
    public boolean canExecuteInTransaction() {
        // MySQL: DDL erzeugt implizite Commits — sonst markiert Flyway die Migration fälschlich als fehlgeschlagen.
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        String schema = resolveSchema(conn);

        ensureSettingsTable(conn);

        if (!columnExists(conn, schema, "unit_atemschutz_settings", "agt_course_id")) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE unit_atemschutz_settings ADD COLUMN agt_course_id BIGINT NULL");
            }
        }

        if (!foreignKeyExists(conn, schema, "unit_atemschutz_settings", "fk_unit_atemschutz_course")) {
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        ALTER TABLE unit_atemschutz_settings
                        ADD CONSTRAINT fk_unit_atemschutz_course
                        FOREIGN KEY (agt_course_id) REFERENCES courses (id) ON DELETE SET NULL
                        """);
            }
        }

        try (Statement st = conn.createStatement()) {
            st.execute("""
                    UPDATE unit_atemschutz_settings uas
                    INNER JOIN courses c
                        ON c.unit_id = uas.unit_id
                       AND c.test_data = FALSE
                       AND LOWER(TRIM(c.name)) = LOWER(TRIM(uas.agt_course_name))
                    SET uas.agt_course_id = c.id
                    WHERE uas.agt_course_id IS NULL
                    """);
        }
    }

    private static void ensureSettingsTable(Connection conn) throws Exception {
        if (tableExists(conn, resolveSchema(conn), "unit_atemschutz_settings")) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE unit_atemschutz_settings (
                        unit_id BIGINT NOT NULL PRIMARY KEY,
                        warn_days INT NOT NULL DEFAULT 90,
                        agt_course_name VARCHAR(64) NOT NULL DEFAULT 'AGT',
                        notification_user_ids TEXT,
                        cc_user_ids TEXT,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        CONSTRAINT fk_unit_atemschutz_settings_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
                    )
                    """);
        }
    }

    private static String resolveSchema(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            if (!rs.next()) {
                throw new IllegalStateException("Kein Datenbankschema für Flyway-Migration V37.");
            }
            String schema = rs.getString(1);
            if (schema == null || schema.isBlank()) {
                throw new IllegalStateException("Kein Datenbankschema für Flyway-Migration V37.");
            }
            return schema;
        }
    }

    private static boolean tableExists(Connection conn, String schema, String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean columnExists(Connection conn, String schema, String table, String column)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean foreignKeyExists(Connection conn, String schema, String table, String constraint)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, constraint);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
