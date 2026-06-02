package de.feuerwehr.manager.dsgvo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feuerwehr.dsgvo")
public record DsgvoProperties(
        int auditRetentionDays,
        String auditSalt,
        boolean auditCleanupEnabled
) {
    public DsgvoProperties {
        if (auditRetentionDays < 1) {
            auditRetentionDays = 90;
        }
    }
}
