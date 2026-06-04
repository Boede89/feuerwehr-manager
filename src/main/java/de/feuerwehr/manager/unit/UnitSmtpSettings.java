package de.feuerwehr.manager.unit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unit_smtp_settings")
public class UnitSmtpSettings {

    @Id
    @Column(name = "unit_id")
    private Long unitId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password", length = 512)
    private String smtpPassword;

    @Column(name = "smtp_from_email", length = 255)
    private String smtpFromEmail;

    @Column(name = "smtp_from_name", length = 255)
    private String smtpFromName;

    @Column(name = "smtp_encryption", length = 16)
    private String smtpEncryption = "TLS";
}
