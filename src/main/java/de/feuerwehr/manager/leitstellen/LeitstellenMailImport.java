package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.berichte.IncidentReport;
import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leitstellen_mail_imports")
public class LeitstellenMailImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_report_id")
    private IncidentReport incidentReport;

    @Column(name = "message_id", nullable = false, length = 512)
    private String messageId;

    @Column(name = "imap_uid")
    private Long imapUid;

    @Column(name = "attachment_name", nullable = false)
    private String attachmentName;

    @Column(name = "attachment_sha256", nullable = false, length = 64)
    private String attachmentSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeitstellenMailKind kind;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
