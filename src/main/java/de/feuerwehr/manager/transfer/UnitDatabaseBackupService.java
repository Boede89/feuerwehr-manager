package de.feuerwehr.manager.transfer;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitRepository;
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
public class UnitDatabaseBackupService {

    public static final String FORMAT = "feuerwehr-manager-unit";
    public static final int VERSION = 1;

    private static final String PERSON_IDS = "SELECT id FROM persons WHERE unit_id = ?";
    private static final String VEHICLE_IDS = "SELECT id FROM vehicles WHERE unit_id = ?";
    private static final String CARRIER_IDS = "SELECT id FROM atemschutz_carriers WHERE unit_id = ?";
    private static final String GROUP_IDS = "SELECT id FROM person_groups WHERE unit_id = ?";
    private static final String INSTRUCTOR_GROUP_IDS = "SELECT id FROM instructor_groups WHERE unit_id = ?";
    private static final String TERMIN_IDS = "SELECT id FROM unit_termine WHERE unit_id = ?";
    private static final String ROLE_IDS = "SELECT id FROM unit_roles WHERE unit_id = ?";
    private static final String UNIT_USER_IDS =
            "SELECT id FROM users WHERE unit_id = ? AND role <> 'SUPER_ADMIN'";

    private static final List<String> DELETE_SQL = List.of(
            "DELETE FROM vehicle_checklist_entries WHERE checklist_id IN "
                    + "(SELECT vc.id FROM vehicle_checklists vc JOIN vehicles v ON vc.vehicle_id = v.id WHERE v.unit_id = ?)",
            "DELETE FROM vehicle_checklists WHERE vehicle_id IN (" + VEHICLE_IDS + ")",
            "DELETE FROM vehicle_checklist_items WHERE template_id IN "
                    + "(SELECT vct.id FROM vehicle_checklist_templates vct JOIN vehicles v ON vct.vehicle_id = v.id WHERE v.unit_id = ?)",
            "DELETE FROM vehicle_checklist_templates WHERE vehicle_id IN (" + VEHICLE_IDS + ")",
            "DELETE FROM vehicle_equipment WHERE vehicle_id IN (" + VEHICLE_IDS + ")",
            "DELETE FROM vehicle_equipment_categories WHERE vehicle_id IN (" + VEHICLE_IDS + ")",
            "DELETE FROM atemschutz_fitness_records WHERE carrier_id IN (" + CARRIER_IDS + ")",
            "DELETE FROM person_group_members WHERE group_id IN (" + GROUP_IDS + ")",
            "DELETE FROM instructor_group_members WHERE group_id IN (" + INSTRUCTOR_GROUP_IDS + ")",
            "DELETE FROM person_course_completions WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM person_attendance WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM person_divera_rics WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM person_qualifications WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM person_equipment WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM person_honors WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM person_emergency_contacts WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM termin_instructor_assignments WHERE termin_id IN (" + TERMIN_IDS + ")",
            "DELETE FROM termin_group_assignments WHERE termin_id IN (" + TERMIN_IDS + ")",
            "DELETE FROM termin_assignments WHERE termin_id IN (" + TERMIN_IDS + ")",
            "DELETE FROM time_entries WHERE person_id IN (" + PERSON_IDS + ")",
            "DELETE FROM user_rfid_cards WHERE user_id IN (" + UNIT_USER_IDS + ")",
            "DELETE FROM user_unit_functions WHERE role_id IN (" + ROLE_IDS + ")",
            "DELETE FROM privacy_consents WHERE user_id IN (" + UNIT_USER_IDS + ")",
            "DELETE FROM atemschutz_email_templates WHERE unit_id = ?",
            "DELETE FROM unit_atemschutz_settings WHERE unit_id = ?",
            "DELETE FROM atemschutz_carriers WHERE unit_id = ?",
            "DELETE FROM person_groups WHERE unit_id = ?",
            "DELETE FROM instructor_groups WHERE unit_id = ?",
            "DELETE FROM persons WHERE unit_id = ?",
            "DELETE FROM courses WHERE unit_id = ?",
            "DELETE FROM qualification_types WHERE unit_id = ?",
            "DELETE FROM vehicles WHERE unit_id = ?",
            "DELETE FROM rooms WHERE unit_id = ?",
            "DELETE FROM users WHERE unit_id = ? AND role <> 'SUPER_ADMIN'",
            "DELETE FROM unit_roles WHERE unit_id = ?",
            "DELETE FROM unit_termine WHERE unit_id = ?",
            "DELETE FROM termin_typen WHERE unit_id = ?",
            "DELETE FROM divera_alarm_samples WHERE unit_id = ?",
            "DELETE FROM test_divera_alarms WHERE unit_id = ?",
            "DELETE FROM unit_smtp_accounts WHERE unit_id = ?",
            "DELETE FROM unit_calendar_accounts WHERE unit_id = ?",
            "DELETE FROM unit_smtp_settings WHERE unit_id = ?",
            "DELETE FROM unit_calendar_settings WHERE unit_id = ?",
            "DELETE FROM unit_divera_settings WHERE unit_id = ?");

    private final JdbcTemplate jdbcTemplate;
    private final UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public byte[] exportSql(long unitId) {
        Unit unit = requireUnit(unitId);
        StringBuilder out = new StringBuilder(8192);
        out.append("-- Feuerwehr-Manager Unit Backup\n");
        out.append("-- format: ").append(FORMAT).append('\n');
        out.append("-- version: ").append(VERSION).append('\n');
        out.append("-- exported_at: ").append(Instant.now()).append('\n');
        out.append("-- unit_id: ").append(unitId).append('\n');
        out.append("-- unit_name: ").append(unit.getName()).append('\n');
        out.append("SET NAMES utf8mb4;\n");
        out.append("SET FOREIGN_KEY_CHECKS=0;\n");

        Object[] params = {unitId};
        SqlBackupCodec.appendReplaceFromQuery(jdbcTemplate, "units", "SELECT * FROM units WHERE id = ?", params, out);
        exportTable("unit_divera_settings", "SELECT * FROM unit_divera_settings WHERE unit_id = ?", params, out);
        exportTable("unit_smtp_settings", "SELECT * FROM unit_smtp_settings WHERE unit_id = ?", params, out);
        exportTable("unit_calendar_settings", "SELECT * FROM unit_calendar_settings WHERE unit_id = ?", params, out);
        exportTable("unit_smtp_accounts", "SELECT * FROM unit_smtp_accounts WHERE unit_id = ?", params, out);
        exportTable("unit_calendar_accounts", "SELECT * FROM unit_calendar_accounts WHERE unit_id = ?", params, out);
        exportTable("unit_roles", "SELECT * FROM unit_roles WHERE unit_id = ?", params, out);
        exportTable("qualification_types", "SELECT * FROM qualification_types WHERE unit_id = ?", params, out);
        exportTable("courses", "SELECT * FROM courses WHERE unit_id = ?", params, out);
        exportTable("persons", "SELECT * FROM persons WHERE unit_id = ?", params, out);
        exportTable("users", "SELECT * FROM users WHERE unit_id = ? AND role <> 'SUPER_ADMIN'", params, out);
        exportTable("person_groups", "SELECT * FROM person_groups WHERE unit_id = ?", params, out);
        exportTable(
                "person_group_members",
                "SELECT pgm.* FROM person_group_members pgm JOIN person_groups pg ON pgm.group_id = pg.id WHERE pg.unit_id = ?",
                params,
                out);
        exportTable("instructor_groups", "SELECT * FROM instructor_groups WHERE unit_id = ?", params, out);
        exportTable(
                "instructor_group_members",
                "SELECT igm.* FROM instructor_group_members igm JOIN instructor_groups ig ON igm.group_id = ig.id WHERE ig.unit_id = ?",
                params,
                out);
        exportPersonChild("person_course_completions", out, unitId);
        exportPersonChild("person_attendance", out, unitId);
        exportPersonChild("person_divera_rics", out, unitId);
        exportPersonChild("person_qualifications", out, unitId);
        exportPersonChild("person_equipment", out, unitId);
        exportPersonChild("person_honors", out, unitId);
        exportPersonChild("person_emergency_contacts", out, unitId);
        exportTable("unit_atemschutz_settings", "SELECT * FROM unit_atemschutz_settings WHERE unit_id = ?", params, out);
        exportTable("atemschutz_email_templates", "SELECT * FROM atemschutz_email_templates WHERE unit_id = ?", params, out);
        exportTable("atemschutz_carriers", "SELECT * FROM atemschutz_carriers WHERE unit_id = ?", params, out);
        exportTable(
                "atemschutz_fitness_records",
                "SELECT afr.* FROM atemschutz_fitness_records afr "
                        + "JOIN atemschutz_carriers ac ON afr.carrier_id = ac.id WHERE ac.unit_id = ?",
                params,
                out);
        exportTable("vehicles", "SELECT * FROM vehicles WHERE unit_id = ?", params, out);
        exportTable(
                "vehicle_equipment_categories",
                "SELECT vec.* FROM vehicle_equipment_categories vec "
                        + "JOIN vehicles v ON vec.vehicle_id = v.id WHERE v.unit_id = ?",
                params,
                out);
        exportTable(
                "vehicle_equipment",
                "SELECT ve.* FROM vehicle_equipment ve JOIN vehicles v ON ve.vehicle_id = v.id WHERE v.unit_id = ?",
                params,
                out);
        exportTable(
                "vehicle_checklist_templates",
                "SELECT vct.* FROM vehicle_checklist_templates vct "
                        + "JOIN vehicles v ON vct.vehicle_id = v.id WHERE v.unit_id = ?",
                params,
                out);
        exportTable(
                "vehicle_checklist_items",
                "SELECT vci.* FROM vehicle_checklist_items vci "
                        + "JOIN vehicle_checklist_templates vct ON vci.template_id = vct.id "
                        + "JOIN vehicles v ON vct.vehicle_id = v.id WHERE v.unit_id = ?",
                params,
                out);
        exportTable(
                "vehicle_checklists",
                "SELECT vc.* FROM vehicle_checklists vc JOIN vehicles v ON vc.vehicle_id = v.id WHERE v.unit_id = ?",
                params,
                out);
        exportTable(
                "vehicle_checklist_entries",
                "SELECT vce.* FROM vehicle_checklist_entries vce "
                        + "JOIN vehicle_checklists vc ON vce.checklist_id = vc.id "
                        + "JOIN vehicles v ON vc.vehicle_id = v.id WHERE v.unit_id = ?",
                params,
                out);
        exportTable("rooms", "SELECT * FROM rooms WHERE unit_id = ?", params, out);
        exportTable("unit_vehicle_types", "SELECT * FROM unit_vehicle_types WHERE unit_id = ?", params, out);
        exportTable("termin_typen", "SELECT * FROM termin_typen WHERE unit_id = ?", params, out);
        exportTable("unit_termine", "SELECT * FROM unit_termine WHERE unit_id = ?", params, out);
        exportTable(
                "termin_instructor_assignments",
                "SELECT tia.* FROM termin_instructor_assignments tia JOIN unit_termine ut ON tia.termin_id = ut.id WHERE ut.unit_id = ?",
                params,
                out);
        exportTable(
                "termin_assignments",
                "SELECT ta.* FROM termin_assignments ta JOIN unit_termine ut ON ta.termin_id = ut.id WHERE ut.unit_id = ?",
                params,
                out);
        exportTable(
                "termin_group_assignments",
                "SELECT tga.* FROM termin_group_assignments tga JOIN unit_termine ut ON tga.termin_id = ut.id WHERE ut.unit_id = ?",
                params,
                out);
        exportPersonChild("time_entries", out, unitId);
        exportTable("divera_alarm_samples", "SELECT * FROM divera_alarm_samples WHERE unit_id = ?", params, out);
        exportTable("test_divera_alarms", "SELECT * FROM test_divera_alarms WHERE unit_id = ?", params, out);
        exportTable(
                "user_rfid_cards",
                "SELECT urc.* FROM user_rfid_cards urc JOIN users u ON urc.user_id = u.id "
                        + "WHERE u.unit_id = ? AND u.role <> 'SUPER_ADMIN'",
                params,
                out);
        exportTable(
                "user_unit_functions",
                "SELECT uuf.* FROM user_unit_functions uuf JOIN unit_roles ur ON uuf.role_id = ur.id WHERE ur.unit_id = ?",
                params,
                out);

        out.append("SET FOREIGN_KEY_CHECKS=1;\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public DatabaseBackupService.DatabaseImportSummary importSql(long unitId, byte[] bytes) {
        requireUnit(unitId);
        String sql = new String(bytes, StandardCharsets.UTF_8);
        SqlBackupCodec.validateFormat(sql, FORMAT);
        Long backupUnitId = SqlBackupCodec.parseHeaderLong(sql, "unit_id");
        if (backupUnitId == null) {
            throw new IllegalArgumentException("Backup enthält keine Einheits-ID.");
        }
        if (!backupUnitId.equals(unitId)) {
            throw new IllegalArgumentException(
                    "Backup gehört zu Einheit " + backupUnitId + ", Import ist für Einheit " + unitId + ".");
        }

        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger inserts = new AtomicInteger();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String deleteSql : DELETE_SQL) {
            deletes.addAndGet(jdbcTemplate.update(deleteSql, unitId));
        }
        SqlBackupCodec.executeScript(
                jdbcTemplate,
                sql,
                statement -> {
                    String upper = statement.trim().toUpperCase(Locale.ROOT);
                    if (upper.startsWith("INSERT INTO") || upper.startsWith("REPLACE INTO")) {
                        inserts.incrementAndGet();
                    }
                });
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        return new DatabaseBackupService.DatabaseImportSummary(deletes.get(), inserts.get());
    }

    private void exportTable(String table, String selectSql, Object[] params, StringBuilder out) {
        SqlBackupCodec.appendQueryInserts(jdbcTemplate, table, selectSql, params, out);
    }

    private void exportPersonChild(String table, StringBuilder out, long unitId) {
        exportTable(
                table,
                "SELECT t.* FROM `" + table + "` t JOIN persons p ON t.person_id = p.id WHERE p.unit_id = ?",
                new Object[] {unitId},
                out);
    }

    private Unit requireUnit(long unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new IllegalArgumentException("Einheit nicht gefunden."));
    }
}
