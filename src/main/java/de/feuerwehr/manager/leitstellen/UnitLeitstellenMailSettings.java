package de.feuerwehr.manager.leitstellen;

import de.feuerwehr.manager.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "unit_leitstellen_mail_settings")
public class UnitLeitstellenMailSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false, unique = true)
    private Unit unit;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "imap_host", length = 255)
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_username", length = 255)
    private String imapUsername;

    @Column(name = "imap_password", length = 512)
    private String imapPassword;

    @Column(name = "imap_encryption", nullable = false, length = 16)
    private String imapEncryption = "SSL";

    @Column(name = "imap_folder", nullable = false, length = 128)
    private String imapFolder = "INBOX";

    @Column(name = "from_filter", length = 255)
    private String fromFilter;

    @Column(name = "subject_filter", length = 255)
    private String subjectFilter = "FAX";

    @Column(name = "depesche_keywords", nullable = false, length = 512)
    private String depescheKeywords = "depesche,alarmdepesche";

    @Column(name = "abschluss_keywords", nullable = false, length = 512)
    private String abschlussKeywords = "abschluss,abschlussbericht";

    @Column(name = "poll_lookback_hours", nullable = false)
    private int pollLookbackHours = 24;

    @Column(name = "match_window_hours", nullable = false)
    private int matchWindowHours = 12;

    @Column(name = "last_poll_at")
    private Instant lastPollAt;

    @Column(name = "last_poll_message", length = 512)
    private String lastPollMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
