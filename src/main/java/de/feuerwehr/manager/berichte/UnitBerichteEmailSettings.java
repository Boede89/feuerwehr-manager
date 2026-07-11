package de.feuerwehr.manager.berichte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unit_berichte_email_settings")
public class UnitBerichteEmailSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private BerichteEmailReportType reportType;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_on_status", length = 16)
    private IncidentReportStatus sendOnStatus;

    @Column(name = "person_ids_json", nullable = false, columnDefinition = "TEXT")
    private String personIdsJson = "[]";

    @Column(name = "manual_emails_json", nullable = false, columnDefinition = "TEXT")
    private String manualEmailsJson = "[]";

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
