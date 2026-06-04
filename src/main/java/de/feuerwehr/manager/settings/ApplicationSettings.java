package de.feuerwehr.manager.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "application_settings")
public class ApplicationSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "test_mode_enabled", nullable = false)
    private boolean testModeEnabled;

    @Column(name = "modules_json")
    private String modulesJson;

    @Column(name = "ff_name")
    private String ffName;

    @Column(name = "ff_strasse")
    private String ffStrasse;

    @Column(name = "ff_ort")
    private String ffOrt;

    @Column(name = "app_url", length = 512)
    private String appUrl;

    @Column(name = "feedback_email")
    private String feedbackEmail;

    @Column(name = "privacy_contact_name")
    private String privacyContactName;

    @Column(name = "privacy_contact_email")
    private String privacyContactEmail;

    @Column(name = "privacy_contact_phone", length = 100)
    private String privacyContactPhone;

    @Column(name = "privacy_hoster")
    private String privacyHoster;

    @Column(name = "logo_base64", columnDefinition = "MEDIUMTEXT")
    private String logoBase64;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort = 587;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password", length = 512)
    private String smtpPassword;

    @Column(name = "smtp_from_email")
    private String smtpFromEmail;

    @Column(name = "smtp_from_name")
    private String smtpFromName;

    @Column(name = "smtp_encryption", length = 16)
    private String smtpEncryption = "TLS";

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
